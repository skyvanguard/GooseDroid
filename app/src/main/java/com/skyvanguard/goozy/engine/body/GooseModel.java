package com.skyvanguard.goozy.engine.body;

import com.skyvanguard.goozy.core.MathUtil;
import com.skyvanguard.goozy.engine.body.BodyState.Expression;
import com.skyvanguard.goozy.engine.body.BodyState.Pose;
import com.skyvanguard.goozy.engine.body.BodyState.Zone;

/**
 * Cuerpo del ganso: postura, caminata y geometría lista para dibujar.
 *
 * El ganso se ve en tres cuartos desde arriba. Cada punto del cuerpo se
 * describe con tres coordenadas de modelo: f (hacia adelante), s (hacia su
 * derecha) y h (altura). {@link #project} las lleva al plano de la pantalla.
 * El cuerpo es una unión de esferas, así que se ve bien desde cualquier ángulo.
 *
 * Java puro: no depende de Android y se prueba en la JVM. No reserva memoria
 * por frame.
 */
public final class GooseModel {
    /** Cuánto se aplasta el suelo visto en diagonal (1 = cenital). */
    static final float GROUND_SQUASH = 0.62f;
    /** Cuánto sube en pantalla una unidad de altura. */
    static final float HEIGHT_FACTOR = 0.78f;

    public static final int GROUP_BODY = 0;
    public static final int GROUP_NECK = 1;
    public static final int GROUP_HEAD = 2;
    public static final int MAX_BALLS = 12;

    private static final int NECK_BALLS = 5;
    private static final float STRIDE = 7f;
    private static final float RUN_SPEED = 160f;
    private static final float LOOK_MAX_DEG = 38f;
    private static final float TUCKED_HEAD_YAW = 165f;
    private static final float BLINK_SECONDS = 0.13f;
    private static final float TWO_PI = (float) (Math.PI * 2);

    // ---- Entrada ----
    public Species species = Species.GOOSE;
    public Pose pose = Pose.NORMAL;
    public Expression expression = Expression.NEUTRAL;
    /** Píxeles de mundo por unidad de modelo. */
    public float scale = 2.5f;

    // ---- Salida: esferas del cuerpo, cuello y cabeza (coordenadas de mundo) ----
    public final float[] ballX = new float[MAX_BALLS];
    public final float[] ballY = new float[MAX_BALLS];
    public final float[] ballR = new float[MAX_BALLS];
    public final int[] ballGroup = new int[MAX_BALLS];
    public int ballCount;
    /** Profundidad media de cada grupo: mayor = más cerca de quien mira. */
    public final float[] groupDepth = new float[3];

    public float headX, headY, headRadius, headDepth;
    public float beakBaseX, beakBaseY, beakTipX, beakTipY, beakHalfWidth, beakDepth;
    public float beakOpen;

    public final float[] eyeX = new float[2];
    public final float[] eyeY = new float[2];
    /** Tamaño aparente del ojo, 0 = no se ve. */
    public final float[] eyeSize = new float[2];
    public float eyeRadius;
    public float blink;

    public final float[] footX = new float[2];
    public final float[] footY = new float[2];
    public final float[] legTopX = new float[2];
    public final float[] legTopY = new float[2];
    /** Dirección de las patas en pantalla (punta de los dedos). */
    public float footDirX, footDirY, footSideX, footSideY;

    public final float[] wingX = new float[2];
    public final float[] wingY = new float[2];
    /** Profundidad del ala de cada lado; negativa = queda oculta detrás del cuerpo. */
    public final float[] wingDepth = new float[2];
    public float wingAngleDeg, wingLength, wingWidth, flap;

    /** Cuello como curva: inicio, control y fin (mundo) y medio grosor. */
    public float neckStartX, neckStartY, neckCtrlX, neckCtrlY, neckEndX, neckEndY, neckHalfWidth;

    public float shadowX, shadowY, shadowRadiusX, shadowRadiusY;
    public float hatX, hatY, hatTilt;
    public float neckAnchorX, neckAnchorY;

    public float eggX, eggY, eggRadiusX, eggRadiusY, eggTiltDeg;

    public float minX, minY, maxX, maxY;

    // ---- Estado interno ----
    private float groundX, groundY, cosH = 0f, sinH = 1f;
    private float time;
    private float lift, neck, tuck, peck, flapBoost;
    private float gaitPhase, gaitAmp;
    private float lookYaw, lookTarget, nextLookAt;
    private float nextBlinkAt = 2f, blinkStart = -1f;
    private boolean hasPrevious;
    private float prevGroundX, prevGroundY;
    private final float[] prevSwing = new float[2];
    private final boolean[] stepPending = new boolean[2];
    private final float[] stepX = new float[2];
    private final float[] stepY = new float[2];
    private float seed = 0.37f;

    // Puntos temporales en coordenadas de modelo
    private float bodyH, sway, hf, hs, hh, headYaw;
    private final float[] tmp = new float[2];

    /**
     * Avanza la animación.
     *
     * @param dt         segundos desde el frame anterior
     * @param x          punto de suelo del ganso, en el mundo
     * @param y          punto de suelo del ganso, en el mundo
     * @param headingDeg hacia dónde mira (0 = derecha, 90 = abajo)
     * @param jump       altura del salto, en el mundo
     * @param neckExtend 0..1, cuánto estira el cuello por pedido del juego
     * @param talk       0..1, cuánto abre el pico
     */
    public void update(float dt, float x, float y, float headingDeg, float jump,
                       float neckExtend, float talk) {
        time += dt;
        groundX = x;
        groundY = y;
        double radians = Math.toRadians(headingDeg);
        cosH = (float) Math.cos(radians);
        sinH = (float) Math.sin(radians);

        updatePosture(dt, neckExtend, talk);
        updateGait(dt, x, y);
        updateLook(dt);
        updateBlink();

        if (species == Species.EGG) {
            layoutEgg(jump / scale);
        } else {
            layoutBody(jump / scale);
        }
    }

    // ============== Postura ==============

    private void updatePosture(float dt, float neckExtend, float talk) {
        float targetLift = 0f, targetNeck = 0.35f, targetTuck = 0f;
        switch (pose) {
            case ALERT: targetLift = 1f; targetNeck = 1f; break;
            case RELAXED: targetLift = -1.2f; targetNeck = 0.1f; break;
            case SLEEPING: targetLift = -2.5f; targetNeck = 0f; targetTuck = 1f; break;
            case EXCITED: targetLift = 0.5f; targetNeck = 0.6f; break;
            case SCARED: targetLift = 1.5f; targetNeck = 1f; break;
            default: break;
        }
        lift = approach(lift, targetLift, dt, 0.18f);
        neck = approach(neck, Math.max(targetNeck, neckExtend), dt, 0.15f);
        tuck = approach(tuck, targetTuck, dt, 0.35f);

        float targetPeck = 0f;
        if (pose == Pose.EATING) {
            float wave = 0.5f + 0.5f * (float) Math.sin(time * 9f);
            targetPeck = wave * wave * wave;
        }
        peck = approach(peck, targetPeck, dt, 0.04f);

        boolean flapping = pose == Pose.EXCITED || pose == Pose.SCARED;
        float targetFlap = flapping ? 0.5f + 0.5f * (float) Math.sin(time * 16f) : 0f;
        flapBoost = approach(flapBoost, 0f, dt, 0.4f);
        flap = Math.max(targetFlap, flapBoost * Math.abs((float) Math.sin(time * 18f)));

        beakOpen = approach(beakOpen, talk, dt, 0.05f);
    }

    /** Aletea un momento (festejo, susto, aterrizaje). */
    public void flapWings() {
        flapBoost = 1f;
    }

    // ============== Caminata ==============

    private void updateGait(float dt, float x, float y) {
        float moved = 0f;
        if (hasPrevious) {
            float dx = x - prevGroundX;
            float dy = y - prevGroundY;
            moved = (float) Math.sqrt(dx * dx + dy * dy) / scale;
        }
        hasPrevious = true;
        prevGroundX = x;
        prevGroundY = y;

        float speed = dt > 0f ? moved * scale / dt : 0f;
        float targetAmp = Math.min(1f, speed / RUN_SPEED * 2f);
        // Un salto de posición (arrastre, teletransporte) no es caminar
        if (moved > STRIDE * 2f) targetAmp = 0f;
        gaitAmp = approach(gaitAmp, targetAmp, dt, 0.12f);
        if (moved <= STRIDE * 2f) {
            gaitPhase = (gaitPhase + moved / STRIDE * (float) Math.PI) % TWO_PI;
        }
    }

    /** Devuelve las pisadas nuevas desde la última llamada (máx. 2) en {@code out} como x,y. */
    public int pollSteps(float[] out) {
        int count = 0;
        for (int i = 0; i < 2; i++) {
            if (!stepPending[i]) continue;
            stepPending[i] = false;
            out[count * 2] = stepX[i];
            out[count * 2 + 1] = stepY[i];
            count++;
        }
        return count;
    }

    // ============== Mirada y parpadeo ==============

    private void updateLook(float dt) {
        if (time >= nextLookAt) {
            boolean idle = gaitAmp < 0.2f && pose != Pose.SLEEPING;
            lookTarget = idle ? (nextRandom() * 2f - 1f) * LOOK_MAX_DEG : 0f;
            nextLookAt = time + 1.5f + nextRandom() * 3f;
        }
        float target = gaitAmp > 0.3f ? 0f : lookTarget;
        lookYaw = approach(lookYaw, target, dt, 0.2f);
    }

    private void updateBlink() {
        if (blinkStart < 0f && time >= nextBlinkAt) {
            blinkStart = time;
        }
        if (blinkStart >= 0f) {
            float p = (time - blinkStart) / BLINK_SECONDS;
            if (p >= 1f) {
                blinkStart = -1f;
                blink = 0f;
                nextBlinkAt = time + 2.5f + nextRandom() * 4f;
            } else {
                blink = 1f - Math.abs(p * 2f - 1f);
            }
        }
        if (pose == Pose.SLEEPING || expression == Expression.SLEEPY && tuck > 0.5f) blink = 1f;
    }

    // ============== Geometría ==============

    private void layoutBody(float jump) {
        Species sp = species;
        float r = sp.bodyRadius;
        float bob = gaitAmp * 0.9f * Math.abs((float) Math.sin(gaitPhase));
        if (pose == Pose.EXCITED && gaitAmp < 0.3f) {
            bob += 1.6f * Math.abs((float) Math.sin(time * 9f));
        }
        float breath = 1f + 0.018f * (float) Math.sin(time * 2.2f);
        bodyH = sp.hipHeight + r * 0.55f + lift + bob + jump;
        sway = gaitAmp * 0.6f * (float) Math.sin(gaitPhase);
        if (pose == Pose.SCARED) sway += 0.35f * (float) Math.sin(time * 55f);

        ballCount = 0;
        float bodyDepth = 0f;
        if (sp == Species.CHICK) {
            bodyDepth += addBall(0f, 0f, bodyH, r * breath, GROUP_BODY);
            bodyDepth += addBall(-r * 0.95f, 0f, bodyH + r * 0.25f, r * 0.3f, GROUP_BODY);
            groupDepth[GROUP_BODY] = bodyDepth / 2f;
        } else {
            bodyDepth += addBall(-0.72f * r, 0f, bodyH - 0.05f * r, 0.85f * r * breath, GROUP_BODY);
            bodyDepth += addBall(0f, 0f, bodyH, r * breath, GROUP_BODY);
            bodyDepth += addBall(0.65f * r, 0f, bodyH + 0.15f * r, 0.85f * r * breath, GROUP_BODY);
            bodyDepth += addBall(-1.3f * r, 0f, bodyH + 0.38f * r, 0.5f * r, GROUP_BODY);
            groupDepth[GROUP_BODY] = bodyDepth / 4f;
        }

        placeHead(sp, r);
        layoutNeck(sp, r);
        addHead(sp);
        layoutWings(r);
        layoutFeet(sp, r, jump);
        layoutShadow(r, jump);
        computeBounds();
    }

    private void placeHead(Species sp, float r) {
        float headR = sp.headRadius;
        float rest, restH, reachF, reachH;
        if (sp.hasNeck()) {
            float baseF = r * 0.62f;
            float baseH = bodyH + r * 0.45f;
            float dfRelax = 3f, dhRelax = 11f * sp.neckLength;
            float dfLong = 5f, dhLong = 17f * sp.neckLength;
            float df = lerp(dfRelax, dfLong, neck);
            float dh = lerp(dhRelax, dhLong, neck);
            double yaw = Math.toRadians(lookYaw);
            rest = baseF + df * (float) Math.cos(yaw);
            float side = df * (float) Math.sin(yaw);
            restH = baseH + dh;
            hs = side;
            reachF = r + 5f;
            reachH = headR + 0.5f;
        } else {
            rest = r * 0.35f;
            restH = bodyH + r * 0.72f + headR * 0.5f;
            hs = 0f;
            reachF = r + 2f;
            reachH = headR + 0.5f;
        }
        hf = lerp(rest, reachF, peck);
        hh = lerp(restH, reachH, peck);

        // Dormido: la cabeza descansa sobre el lomo, mirando hacia atrás
        float tuckF = -2f, tuckS = 1.5f, tuckH = bodyH + r * 0.75f + headR * 0.6f;
        hf = lerp(hf, tuckF, tuck);
        hs = lerp(hs, tuckS, tuck);
        hh = lerp(hh, tuckH, tuck);
        headYaw = lerp(lookYaw, TUCKED_HEAD_YAW, tuck);
        hs += sway;
    }

    private void layoutNeck(Species sp, float r) {
        if (!sp.hasNeck()) {
            groupDepth[GROUP_NECK] = Float.NEGATIVE_INFINITY;
            float[] p = project(0.4f * r, sway, bodyH + r * 0.7f);
            neckAnchorX = p[0];
            neckAnchorY = p[1];
            return;
        }
        float bf = r * 0.62f, bs = sway, bh = bodyH + r * 0.45f;
        float cf = bf - 1.5f + (hf - bf) * 0.15f;
        float cs = (bs + hs) * 0.5f;
        float ch = (bh + hh) * 0.5f + 1.5f;
        float[] p = project(bf, bs, bh);
        neckStartX = p[0];
        neckStartY = p[1];
        p = project(cf, cs, ch);
        neckCtrlX = p[0];
        neckCtrlY = p[1];
        p = project(hf, hs, hh);
        neckEndX = p[0];
        neckEndY = p[1];
        neckHalfWidth = 3.3f * scale;
        float depth = 0f;
        for (int i = 0; i < NECK_BALLS; i++) {
            float t = i / (float) NECK_BALLS;
            float u = 1f - t;
            float f = u * u * bf + 2 * u * t * cf + t * t * hf;
            float s = u * u * bs + 2 * u * t * cs + t * t * hs;
            float h = u * u * bh + 2 * u * t * ch + t * t * hh;
            depth += addBall(f, s, h, lerp(3.7f, 3.0f, t), GROUP_NECK);
            if (i == 1) {
                neckAnchorX = ballX[ballCount - 1];
                neckAnchorY = ballY[ballCount - 1];
            }
        }
        groupDepth[GROUP_NECK] = depth / NECK_BALLS;
    }

    private void addHead(Species sp) {
        float r = sp.headRadius;
        headDepth = addBall(hf, hs, hh, r, GROUP_HEAD);
        groupDepth[GROUP_HEAD] = headDepth;
        headX = ballX[ballCount - 1];
        headY = ballY[ballCount - 1];
        headRadius = r * scale;

        double yaw = Math.toRadians(headYaw);
        float fx = (float) Math.cos(yaw);
        float fs = (float) Math.sin(yaw);

        // Pico: sale del frente de la cabeza, apenas inclinado hacia abajo
        float baseF = hf + fx * r * 0.82f, baseS = hs + fs * r * 0.82f, baseH = hh - 0.5f;
        float len = sp.beakLength;
        float droop = 0.8f + 0.6f * peck;
        float[] p = project(baseF, baseS, baseH);
        beakBaseX = p[0];
        beakBaseY = p[1];
        p = project(baseF + fx * len, baseS + fs * len, baseH - droop);
        beakTipX = p[0];
        beakTipY = p[1];
        beakHalfWidth = sp.beakWidth * 0.5f * scale;
        beakDepth = headDepth + viewDepth(fx, fs, -0.15f) * r;

        // Ojos a los costados, un poco hacia adelante y arriba
        eyeRadius = r * 0.24f * scale;
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? -1f : 1f;
            float nf = 0.42f * fx - side * 0.72f * fs;
            float ns = 0.42f * fs + side * 0.72f * fx;
            float nh = 0.38f;
            p = project(hf + nf * r, hs + ns * r, hh + nh * r);
            eyeX[i] = p[0];
            eyeY[i] = p[1];
            float facing = viewDepth(nf, ns, nh);
            eyeSize[i] = facing < -0.05f ? 0f : MathUtil.clamp(0.55f + 0.6f * facing, 0.5f, 1f);
        }

        p = project(hf, hs, hh + r * 0.95f);
        hatX = p[0];
        hatY = p[1];
        hatTilt = (float) Math.toDegrees(Math.atan2(fs * cosH + fx * sinH, 4f)) * 0.15f;
    }

    private void layoutWings(float r) {
        float lenScale = (float) Math.sqrt(cosH * cosH + sinH * sinH * GROUND_SQUASH * GROUND_SQUASH);
        wingLength = r * 1.25f * scale * lenScale;
        wingWidth = r * 0.58f * scale;
        wingAngleDeg = (float) Math.toDegrees(Math.atan2(sinH * GROUND_SQUASH, cosH));
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? -1f : 1f;
            float[] p = project(-0.2f * r, sway + side * r * 0.72f, bodyH + 0.18f * r + flap * 2.5f);
            wingX[i] = p[0];
            wingY[i] = p[1];
            // Solo se ve el ala del costado que mira hacia quien mira
            wingDepth[i] = viewDepth(0f, side, 0f) - 0.45f;
        }
    }

    private void layoutFeet(Species sp, float r, float jump) {
        footDirX = cosH;
        footDirY = sinH * GROUND_SQUASH;
        footSideX = -sinH;
        footSideY = cosH * GROUND_SQUASH;
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? -1f : 1f;
            float phase = gaitPhase + (i == 0 ? 0f : (float) Math.PI);
            float swing = (float) Math.cos(phase);
            float stepLift = gaitAmp * 1.8f * Math.max(0f, swing);
            float f = 0.1f * r + gaitAmp * 3.2f * (float) Math.sin(phase);
            float s = side * sp.footSide;
            float[] p = project(f, s, stepLift + jump);
            footX[i] = p[0];
            footY[i] = p[1];
            p = project(0.15f * r, s * 0.8f + sway * 0.5f, sp.hipHeight + lift * 0.5f + jump);
            legTopX[i] = p[0];
            legTopY[i] = p[1];

            // Pisada: el pie termina de bajar
            if (gaitAmp > 0.15f && prevSwing[i] > 0f && swing <= 0f && jump < 0.5f) {
                p = project(f, s, 0f);
                stepX[i] = p[0];
                stepY[i] = p[1];
                stepPending[i] = true;
            }
            prevSwing[i] = swing;
        }
    }

    private void layoutShadow(float r, float jump) {
        float[] p = project(0f, 0f, 0f);
        shadowX = p[0];
        shadowY = p[1];
        float shrink = 1f - 0.5f * MathUtil.clamp(jump / 20f, 0f, 1f);
        shadowRadiusX = r * 1.35f * scale * shrink;
        shadowRadiusY = shadowRadiusX * GROUND_SQUASH;
    }

    private void layoutEgg(float jump) {
        ballCount = 0;
        float rx = Species.EGG.bodyRadius;
        float ry = rx * 1.3f;
        float[] p = project(0f, 0f, ry + jump);
        eggX = p[0];
        eggY = p[1];
        eggRadiusX = rx * scale;
        eggRadiusY = ry * scale;
        float wobble = pose == Pose.EXCITED ? 10f : 4f;
        eggTiltDeg = wobble * (float) Math.sin(time * 3f);
        neckAnchorX = hatX = eggX;
        neckAnchorY = eggY;
        hatY = eggY - eggRadiusY;
        headX = eggX;
        headY = eggY;
        headRadius = eggRadiusX;
        layoutShadow(rx, jump);
        minX = eggX - eggRadiusX;
        maxX = eggX + eggRadiusX;
        minY = eggY - eggRadiusY;
        maxY = Math.max(eggY + eggRadiusY, shadowY + shadowRadiusY);
    }

    private void computeBounds() {
        minX = shadowX - shadowRadiusX;
        maxX = shadowX + shadowRadiusX;
        minY = shadowY - shadowRadiusY;
        maxY = shadowY + shadowRadiusY;
        for (int i = 0; i < ballCount; i++) {
            include(ballX[i] - ballR[i], ballY[i] - ballR[i]);
            include(ballX[i] + ballR[i], ballY[i] + ballR[i]);
        }
        include(beakTipX, beakTipY);
        // Lugar para un sombrero
        include(hatX, hatY - headRadius * 1.6f);
        for (int i = 0; i < 2; i++) include(footX[i], footY[i]);
    }

    private void include(float x, float y) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
    }

    // ============== Tacto ==============

    /** Parte del cuerpo bajo el punto (coordenadas de mundo), o NONE. */
    public Zone zoneAt(float x, float y) {
        if (species == Species.EGG) {
            float dx = (x - eggX) / (eggRadiusX * 1.2f);
            float dy = (y - eggY) / (eggRadiusY * 1.2f);
            return dx * dx + dy * dy <= 1f ? Zone.BODY : Zone.NONE;
        }
        float slack = 1.25f;
        if (distToSegment(x, y, beakBaseX, beakBaseY, beakTipX, beakTipY) < beakHalfWidth * 2.2f) {
            return Zone.BEAK;
        }
        if (dist(x, y, headX, headY) < headRadius * slack) return Zone.HEAD;
        for (int i = 0; i < 2; i++) {
            if (dist(x, y, footX[i], footY[i]) < 3.5f * scale) return Zone.FEET;
        }
        int tail = species == Species.CHICK ? 1 : 3;
        if (tail < ballCount && dist(x, y, ballX[tail], ballY[tail]) < ballR[tail] * 1.6f) {
            return Zone.TAIL;
        }
        for (int i = 0; i < 2; i++) {
            if (wingDepth[i] > -0.1f && dist(x, y, wingX[i], wingY[i]) < wingWidth) {
                return i == 0 ? Zone.WING_LEFT : Zone.WING_RIGHT;
            }
        }
        for (int i = 0; i < ballCount; i++) {
            if (dist(x, y, ballX[i], ballY[i]) < ballR[i] * slack) {
                boolean lower = ballGroup[i] == GROUP_BODY && y > ballY[i] + ballR[i] * 0.35f;
                return lower ? Zone.BELLY : Zone.BODY;
            }
        }
        return Zone.NONE;
    }

    // ============== Utilidades ==============

    private float addBall(float f, float s, float h, float r, int group) {
        float[] p = project(f, s, h);
        int i = ballCount++;
        ballX[i] = p[0];
        ballY[i] = p[1];
        ballR[i] = r * scale;
        ballGroup[i] = group;
        return pointDepth(f, s, h);
    }

    /** Coordenadas de modelo → mundo. Devuelve un arreglo reutilizado. */
    float[] project(float f, float s, float h) {
        float gx = f * cosH - s * sinH;
        float gy = f * sinH + s * cosH;
        tmp[0] = groundX + scale * gx;
        tmp[1] = groundY + scale * (gy * GROUND_SQUASH - h * HEIGHT_FACTOR);
        return tmp;
    }

    /** Qué tan cerca de quien mira está un punto (mayor = adelante). */
    private float pointDepth(float f, float s, float h) {
        float gy = f * sinH + s * cosH;
        return gy * HEIGHT_FACTOR + h * GROUND_SQUASH * 0.2f;
    }

    /** Cuánto mira hacia quien mira una dirección (f, s, h) del cuerpo. */
    private float viewDepth(float f, float s, float h) {
        float gy = f * sinH + s * cosH;
        return gy * HEIGHT_FACTOR + h * GROUND_SQUASH;
    }

    private float nextRandom() {
        seed = (seed * 9301f + 49297f) % 233280f;
        return seed / 233280f;
    }

    /** Acerca un valor a su objetivo con una constante de tiempo, sin depender de los fps. */
    static float approach(float value, float target, float dt, float timeConstant) {
        if (timeConstant <= 0f) return target;
        float k = 1f - (float) Math.exp(-dt / timeConstant);
        return value + (target - value) * k;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float distToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax, vy = by - ay;
        float len2 = vx * vx + vy * vy;
        float t = len2 == 0f ? 0f : MathUtil.clamp(((px - ax) * vx + (py - ay) * vy) / len2, 0f, 1f);
        return dist(px, py, ax + vx * t, ay + vy * t);
    }
}
