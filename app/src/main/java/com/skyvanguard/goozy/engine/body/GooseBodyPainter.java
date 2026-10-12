package com.skyvanguard.goozy.engine.body;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import com.skyvanguard.goozy.engine.body.BodyState.Expression;

/**
 * Dibuja un {@link GooseModel}: estilo cartoon con un solo contorno por parte,
 * sombreado suave compartido por grupo y ojos dentro de la cabeza.
 * Reutiliza todos los objetos gráficos; no reserva memoria por frame.
 */
public final class GooseBodyPainter {
    private static final int SHADOW_COLOR = Color.argb(70, 0, 0, 0);
    private static final int INK = 0xFF26262E;
    private static final int BLUSH = Color.argb(120, 255, 120, 150);
    private static final int EGG_COLOR = 0xFFFFF4DC;
    private static final int EGG_SPOT = 0xFFE8C98E;
    /** Grosor del contorno en unidades de modelo. */
    private static final float OUTLINE_UNITS = 0.75f;

    private int bodyColor = Color.WHITE;
    private int outlineColor = 0xFF8A8A96;
    private int footColor = 0xFFFFA500;
    private int beakColor = 0xFFFFA500;
    private int eyeColor = Color.BLACK;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shaded = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accessory = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path scratch = new Path();
    private final Path unitWing = new Path();
    private final Path unitFeathers = new Path();
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private RadialGradient bodyShader;
    private int shaderColor;
    private final int[] groupOrder = new int[3];
    private final Paint neckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int HULL_SAMPLES = 20;
    private static final int HULL_MAX = HULL_SAMPLES * GooseModel.MAX_BALLS;
    private static final float[] UNIT_COS = new float[HULL_SAMPLES];
    private static final float[] UNIT_SIN = new float[HULL_SAMPLES];
    private final float[] hullInX = new float[HULL_MAX];
    private final float[] hullInY = new float[HULL_MAX];
    private final float[] hullX = new float[HULL_MAX + 1];
    private final float[] hullY = new float[HULL_MAX + 1];
    private final int[] order = new int[HULL_MAX];

    static {
        for (int i = 0; i < HULL_SAMPLES; i++) {
            double a = Math.PI * 2 * i / HULL_SAMPLES;
            UNIT_COS[i] = (float) Math.cos(a);
            UNIT_SIN[i] = (float) Math.sin(a);
        }
    }

    public GooseBodyPainter() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        neckPaint.setStyle(Paint.Style.STROKE);
        neckPaint.setStrokeCap(Paint.Cap.ROUND);
        buildUnitWing();
    }

    /** Colores del ganso; un contorno claro se oscurece para que se lea. */
    public void setColors(int body, int outline, int foot, int beak, int eye) {
        bodyColor = body;
        outlineColor = blend(outline, INK, 0.55f);
        footColor = foot;
        beakColor = beak;
        eyeColor = eye;
    }

    public void draw(Canvas canvas, GooseModel m, boolean shadow, int hatId, int accessoryId) {
        float ow = OUTLINE_UNITS * m.scale;
        if (shadow) drawShadow(canvas, m);
        if (m.species == Species.EGG) {
            drawEgg(canvas, m, ow);
            drawHat(canvas, m, hatId);
            return;
        }
        drawLegs(canvas, m, ow);
        sortGroups(m);
        for (int g : groupOrder) {
            if (m.groupDepth[g] == Float.NEGATIVE_INFINITY) continue;
            if (g == GooseModel.GROUP_HEAD) {
                drawHead(canvas, m, ow);
            } else if (g == GooseModel.GROUP_NECK) {
                drawNeck(canvas, m, ow);
            } else {
                drawBody(canvas, m, ow);
                drawWings(canvas, m, ow);
            }
        }
        drawNeckAccessory(canvas, m, accessoryId);
        drawHat(canvas, m, hatId);
    }

    // ============== Partes ==============

    private void drawShadow(Canvas canvas, GooseModel m) {
        fill.setShader(null);
        fill.setColor(SHADOW_COLOR);
        rect.set(m.shadowX - m.shadowRadiusX, m.shadowY - m.shadowRadiusY,
                m.shadowX + m.shadowRadiusX, m.shadowY + m.shadowRadiusY);
        canvas.drawOval(rect, fill);
    }

    private void drawLegs(Canvas canvas, GooseModel m, float ow) {
        float u = m.scale;
        int legColor = blend(footColor, INK, 0.25f);
        for (int i = 0; i < 2; i++) {
            stroke.setColor(legColor);
            stroke.setStrokeWidth(1.1f * u);
            canvas.drawLine(m.legTopX[i], m.legTopY[i], m.footX[i], m.footY[i], stroke);

            float fx = m.footX[i], fy = m.footY[i];
            float dx = m.footDirX * u, dy = m.footDirY * u;
            float sx = m.footSideX * u, sy = m.footSideY * u;
            path.rewind();
            path.moveTo(fx - dx * 0.6f, fy - dy * 0.6f);
            path.lineTo(fx + dx * 2.0f - sx * 1.5f, fy + dy * 2.0f - sy * 1.5f);
            path.quadTo(fx + dx * 2.9f, fy + dy * 2.9f,
                    fx + dx * 2.0f + sx * 1.5f, fy + dy * 2.0f + sy * 1.5f);
            path.close();
            fill.setShader(null);
            fill.setColor(footColor);
            canvas.drawPath(path, fill);
            stroke.setColor(outlineColor);
            stroke.setStrokeWidth(ow * 0.7f);
            canvas.drawPath(path, stroke);
        }
    }

    /**
     * Silueta suave del cuerpo: la envolvente convexa de sus esferas, con las
     * esquinas redondeadas. Así no se notan los bultos donde se unen.
     */
    private void drawBody(Canvas canvas, GooseModel m, float ow) {
        int n = 0;
        float cx = 0f, cy = 0f, radius = 0f;
        int balls = 0;
        for (int i = 0; i < m.ballCount; i++) {
            if (m.ballGroup[i] != GooseModel.GROUP_BODY) continue;
            for (int k = 0; k < HULL_SAMPLES; k++) {
                hullInX[n] = m.ballX[i] + UNIT_COS[k] * m.ballR[i];
                hullInY[n] = m.ballY[i] + UNIT_SIN[k] * m.ballR[i];
                n++;
            }
            cx += m.ballX[i];
            cy += m.ballY[i];
            balls++;
        }
        if (balls == 0) return;
        cx /= balls;
        cy /= balls;
        for (int i = 0; i < m.ballCount; i++) {
            if (m.ballGroup[i] != GooseModel.GROUP_BODY) continue;
            radius = Math.max(radius, (float) Math.hypot(m.ballX[i] - cx, m.ballY[i] - cy) + m.ballR[i]);
        }
        int h = convexHull(n);
        path.rewind();
        path.moveTo((hullX[h - 1] + hullX[0]) * 0.5f, (hullY[h - 1] + hullY[0]) * 0.5f);
        for (int k = 0; k < h; k++) {
            int next = (k + 1) % h;
            path.quadTo(hullX[k], hullY[k],
                    (hullX[k] + hullX[next]) * 0.5f, (hullY[k] + hullY[next]) * 0.5f);
        }
        path.close();
        applyShading(cx, cy, radius);
        canvas.drawPath(path, shaded);
        stroke.setColor(outlineColor);
        stroke.setStrokeWidth(ow * 2f);
        canvas.drawPath(path, stroke);
    }

    /** Cuello como un tubo: contorno ancho y relleno encima. */
    private void drawNeck(Canvas canvas, GooseModel m, float ow) {
        path.rewind();
        path.moveTo(m.neckStartX, m.neckStartY);
        path.quadTo(m.neckCtrlX, m.neckCtrlY, m.neckEndX, m.neckEndY);
        stroke.setColor(outlineColor);
        stroke.setStrokeWidth(m.neckHalfWidth * 2f + ow * 2f);
        canvas.drawPath(path, stroke);
        float cx = (m.neckStartX + m.neckEndX) * 0.5f;
        float cy = (m.neckStartY + m.neckEndY) * 0.5f;
        float radius = (float) Math.hypot(m.neckEndX - m.neckStartX, m.neckEndY - m.neckStartY) * 0.5f
                + m.neckHalfWidth;
        applyShading(cx, cy, radius);
        neckPaint.setShader(shaded.getShader());
        neckPaint.setStrokeWidth(m.neckHalfWidth * 2f);
        canvas.drawPath(path, neckPaint);
    }

    /** Envolvente convexa (cadena monótona) de hullIn hacia hull. Devuelve la cantidad de puntos. */
    private int convexHull(int n) {
        for (int i = 0; i < n; i++) order[i] = i;
        for (int i = 1; i < n; i++) {
            int v = order[i];
            int j = i - 1;
            while (j >= 0 && (hullInX[order[j]] > hullInX[v]
                    || hullInX[order[j]] == hullInX[v] && hullInY[order[j]] > hullInY[v])) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = v;
        }
        int k = 0;
        for (int i = 0; i < n; i++) {
            int p = order[i];
            while (k >= 2 && cross(k, p) <= 0f) k--;
            hullX[k] = hullInX[p];
            hullY[k] = hullInY[p];
            k++;
        }
        int lower = k + 1;
        for (int i = n - 2; i >= 0; i--) {
            int p = order[i];
            while (k >= lower && cross(k, p) <= 0f) k--;
            hullX[k] = hullInX[p];
            hullY[k] = hullInY[p];
            k++;
        }
        return k - 1;
    }

    /** Producto cruz entre los dos últimos puntos de la envolvente y el candidato. */
    private float cross(int k, int p) {
        float ox = hullX[k - 2], oy = hullY[k - 2], ax = hullX[k - 1], ay = hullY[k - 1];
        return (ax - ox) * (hullInY[p] - oy) - (ay - oy) * (hullInX[p] - ox);
    }

    private void drawWings(Canvas canvas, GooseModel m, float ow) {
        int wingColor = blend(bodyColor, outlineColor, 0.12f);
        for (int i = 0; i < 2; i++) {
            if (m.wingDepth[i] <= -0.1f) continue;
            float lift = m.flap * m.wingWidth * 0.5f;
            matrix.setScale(m.wingLength, m.wingWidth * (1f + m.flap * 0.5f));
            matrix.postRotate(m.wingAngleDeg);
            matrix.postTranslate(m.wingX[i], m.wingY[i] - lift);
            unitWing.transform(matrix, scratch);
            fill.setShader(null);
            fill.setColor(wingColor);
            canvas.drawPath(scratch, fill);
            stroke.setColor(outlineColor);
            stroke.setStrokeWidth(ow * 0.85f);
            canvas.drawPath(scratch, stroke);
            unitFeathers.transform(matrix, scratch);
            stroke.setStrokeWidth(ow * 0.5f);
            canvas.drawPath(scratch, stroke);
        }
    }

    private void drawHead(Canvas canvas, GooseModel m, float ow) {
        boolean beakInFront = m.beakDepth >= m.headDepth;
        if (!beakInFront) drawBeak(canvas, m, ow);
        fill.setShader(null);
        fill.setColor(outlineColor);
        canvas.drawCircle(m.headX, m.headY, m.headRadius + ow, fill);
        applyShading(m.headX, m.headY, m.headRadius);
        canvas.drawCircle(m.headX, m.headY, m.headRadius, shaded);
        if (beakInFront) drawBeak(canvas, m, ow);
        for (int i = 0; i < 2; i++) {
            if (m.eyeSize[i] > 0f) drawEye(canvas, m, i);
        }
    }

    private void drawBeak(Canvas canvas, GooseModel m, float ow) {
        float bx = m.beakBaseX, by = m.beakBaseY, tx = m.beakTipX, ty = m.beakTipY;
        float vx = tx - bx, vy = ty - by;
        float len = (float) Math.hypot(vx, vy);
        if (len < 0.01f) return;
        float px = -vy / len * m.beakHalfWidth, py = vx / len * m.beakHalfWidth;
        if (py > 0f) {
            px = -px;
            py = -py;
        }
        float open = m.beakOpen * m.beakHalfWidth * 1.6f;

        fill.setShader(null);
        fill.setColor(beakColor);
        stroke.setColor(outlineColor);
        stroke.setStrokeWidth(ow * 0.7f);
        // Mandíbula inferior, visible cuando abre el pico
        if (open > 0.5f) {
            path.rewind();
            path.moveTo(bx - px * 0.6f, by - py * 0.6f);
            path.lineTo(tx - vx * 0.15f, ty - vy * 0.15f + open);
            path.lineTo(bx, by);
            path.close();
            fill.setColor(blend(beakColor, INK, 0.2f));
            canvas.drawPath(path, fill);
            canvas.drawPath(path, stroke);
            fill.setColor(beakColor);
        }
        path.rewind();
        path.moveTo(bx + px, by + py);
        path.quadTo(bx + vx * 0.75f + px * 0.75f, by + vy * 0.75f + py * 0.75f, tx, ty);
        path.quadTo(bx + vx * 0.6f - px * 0.55f, by + vy * 0.6f - py * 0.55f,
                bx - px * 0.8f, by - py * 0.8f);
        path.close();
        canvas.drawPath(path, fill);
        canvas.drawPath(path, stroke);
    }

    private void drawEye(Canvas canvas, GooseModel m, int i) {
        float r = m.eyeRadius * m.eyeSize[i];
        float x = m.eyeX[i], y = m.eyeY[i];
        Expression e = m.expression;
        float toBeak = Math.signum(m.beakTipX - m.headX);
        if (toBeak == 0f) toBeak = 1f;

        if (e == Expression.HAPPY || e == Expression.JOY || e == Expression.LOVE) {
            fill.setShader(null);
            fill.setColor(BLUSH);
            rect.set(x + toBeak * r * 0.2f - r, y + r * 0.9f, x + toBeak * r * 0.2f + r, y + r * 1.6f);
            canvas.drawOval(rect, fill);
        }
        stroke.setColor(eyeColor);
        stroke.setStrokeWidth(r * 0.55f);
        if (m.blink > 0.5f || e == Expression.SLEEPY) {
            path.rewind();
            path.moveTo(x - r, y);
            path.quadTo(x, y + r * 0.6f, x + r, y);
            canvas.drawPath(path, stroke);
            return;
        }
        if (e == Expression.JOY) {
            path.rewind();
            path.moveTo(x - r, y + r * 0.35f);
            path.quadTo(x, y - r * 1.1f, x + r, y + r * 0.35f);
            canvas.drawPath(path, stroke);
            return;
        }
        if (e == Expression.LOVE) {
            drawHeart(canvas, x, y, r * 1.5f, 0xFFE8435A);
            return;
        }
        fill.setShader(null);
        if (e == Expression.SURPRISED) {
            fill.setColor(Color.WHITE);
            canvas.drawCircle(x, y, r * 1.35f, fill);
            stroke.setStrokeWidth(r * 0.3f);
            canvas.drawCircle(x, y, r * 1.35f, stroke);
            fill.setColor(eyeColor);
            canvas.drawCircle(x, y, r * 0.6f, fill);
            return;
        }
        fill.setColor(eyeColor);
        canvas.drawCircle(x, y, r, fill);
        fill.setColor(Color.WHITE);
        canvas.drawCircle(x - r * 0.3f, y - r * 0.35f, r * 0.34f, fill);
        if (e == Expression.ANGRY || e == Expression.SAD) {
            // Párpado inclinado: hacia el pico si está enojado, hacia afuera si está triste
            float inner = e == Expression.ANGRY ? r * 0.2f : -r * 0.9f;
            float outer = e == Expression.ANGRY ? -r * 0.9f : r * 0.2f;
            stroke.setColor(INK);
            stroke.setStrokeWidth(r * 0.45f);
            canvas.drawLine(x + toBeak * r * 1.2f, y + inner - r * 0.5f,
                    x - toBeak * r * 1.2f, y + outer - r * 0.5f, stroke);
        }
    }

    private void drawEgg(Canvas canvas, GooseModel m, float ow) {
        canvas.save();
        canvas.rotate(m.eggTiltDeg, m.eggX, m.eggY + m.eggRadiusY);
        float rx = m.eggRadiusX, ry = m.eggRadiusY, cx = m.eggX, cy = m.eggY;
        // Más angosto arriba que abajo
        path.rewind();
        path.moveTo(cx, cy - ry);
        path.cubicTo(cx + rx * 0.72f, cy - ry, cx + rx, cy - ry * 0.2f, cx + rx, cy + ry * 0.2f);
        path.cubicTo(cx + rx, cy + ry * 0.78f, cx + rx * 0.55f, cy + ry, cx, cy + ry);
        path.cubicTo(cx - rx * 0.55f, cy + ry, cx - rx, cy + ry * 0.78f, cx - rx, cy + ry * 0.2f);
        path.cubicTo(cx - rx, cy - ry * 0.2f, cx - rx * 0.72f, cy - ry, cx, cy - ry);
        path.close();
        applyShading(cx, cy, Math.max(rx, ry), EGG_COLOR);
        canvas.drawPath(path, shaded);
        stroke.setColor(outlineColor);
        stroke.setStrokeWidth(ow * 2f);
        canvas.drawPath(path, stroke);
        fill.setColor(EGG_SPOT);
        float s = m.eggRadiusX;
        canvas.drawCircle(m.eggX - s * 0.35f, m.eggY - s * 0.3f, s * 0.14f, fill);
        canvas.drawCircle(m.eggX + s * 0.3f, m.eggY + s * 0.15f, s * 0.11f, fill);
        canvas.drawCircle(m.eggX - s * 0.1f, m.eggY + s * 0.55f, s * 0.09f, fill);
        canvas.restore();
    }

    // ============== Accesorios ==============

    private void drawHat(Canvas canvas, GooseModel m, int hatId) {
        if (hatId <= 0) return;
        float k = m.headRadius / 10f;
        canvas.save();
        canvas.translate(m.hatX, m.hatY);
        canvas.rotate(m.hatTilt);
        canvas.scale(k, k);
        accessory.setStyle(Paint.Style.FILL);
        switch (hatId) {
            case 1: // Moño
                accessory.setColor(0xFFFF69B4);
                rect.set(-11, -6, -1, 2);
                canvas.drawOval(rect, accessory);
                rect.set(1, -6, 11, 2);
                canvas.drawOval(rect, accessory);
                accessory.setColor(0xFFE0457F);
                canvas.drawCircle(0, -2, 2.6f, accessory);
                break;
            case 2: // Galera
                accessory.setColor(0xFF2E2E36);
                canvas.drawRoundRect(-11, -3, 11, 1, 1.5f, 1.5f, accessory);
                canvas.drawRoundRect(-7, -17, 7, -2, 1.5f, 1.5f, accessory);
                accessory.setColor(0xFFD94F4F);
                canvas.drawRect(-7, -6, 7, -3.5f, accessory);
                break;
            case 3: // Corona
                accessory.setColor(0xFFFFC83D);
                path.rewind();
                path.moveTo(-8, 0);
                path.lineTo(-8, -9);
                path.lineTo(-4, -4);
                path.lineTo(0, -11);
                path.lineTo(4, -4);
                path.lineTo(8, -9);
                path.lineTo(8, 0);
                path.close();
                canvas.drawPath(path, accessory);
                accessory.setColor(0xFFE8435A);
                canvas.drawCircle(0, -3, 1.6f, accessory);
                break;
            case 4: // Gorra con visera hacia donde mira
                float visor = Math.signum(m.beakTipX - m.headX);
                if (visor == 0f) visor = 1f;
                accessory.setColor(0xFF3D6FE0);
                rect.set(-9, -10, 9, 6);
                canvas.drawArc(rect, 180, 180, true, accessory);
                canvas.drawRoundRect(visor > 0 ? 0 : -15, -2.5f, visor > 0 ? 15 : 0, 0.5f,
                        1.5f, 1.5f, accessory);
                break;
            default:
                break;
        }
        canvas.restore();
    }

    private void drawNeckAccessory(Canvas canvas, GooseModel m, int accessoryId) {
        if (accessoryId <= 0) return;
        if (accessoryId == 2) {
            drawGlasses(canvas, m);
            return;
        }
        float k = m.headRadius / 10f;
        canvas.save();
        canvas.translate(m.neckAnchorX, m.neckAnchorY);
        canvas.scale(k, k);
        accessory.setStyle(Paint.Style.FILL);
        switch (accessoryId) {
            case 1: // Bufanda
                accessory.setColor(0xFFD94F4F);
                canvas.drawRoundRect(-8, -3, 8, 3, 3, 3, accessory);
                canvas.drawRoundRect(3, 0, 7, 12, 2, 2, accessory);
                break;
            case 3: // Collar
                accessory.setColor(0xFF8B5A2B);
                canvas.drawRoundRect(-7, -2, 7, 2, 2, 2, accessory);
                accessory.setColor(0xFFFFC83D);
                canvas.drawCircle(0, 4, 2.4f, accessory);
                break;
            case 4: // Moño de corbata
                accessory.setColor(0xFF9E1B32);
                path.rewind();
                path.moveTo(0, 0);
                path.lineTo(-8, -4);
                path.lineTo(-8, 4);
                path.close();
                path.moveTo(0, 0);
                path.lineTo(8, -4);
                path.lineTo(8, 4);
                path.close();
                canvas.drawPath(path, accessory);
                canvas.drawCircle(0, 0, 2, accessory);
                break;
            default:
                break;
        }
        canvas.restore();
    }

    private void drawGlasses(Canvas canvas, GooseModel m) {
        stroke.setColor(INK);
        stroke.setStrokeWidth(m.eyeRadius * 0.35f);
        int visible = 0;
        for (int i = 0; i < 2; i++) {
            if (m.eyeSize[i] <= 0f) continue;
            canvas.drawCircle(m.eyeX[i], m.eyeY[i], m.eyeRadius * 1.9f * m.eyeSize[i], stroke);
            visible++;
        }
        if (visible == 2) {
            canvas.drawLine(m.eyeX[0], m.eyeY[0], m.eyeX[1], m.eyeY[1], stroke);
        }
    }

    // ============== Utilidades ==============

    /**
     * Cuello y cabeza van juntos y la cabeza siempre encima del cuello; ese par
     * se dibuja antes o después del cuerpo según quién esté más cerca.
     */
    private void sortGroups(GooseModel m) {
        float neckDepth = m.groupDepth[GooseModel.GROUP_NECK];
        // Sin cuello (pollito) la cabeza va encima del cuerpo: siempre se ve
        boolean headBehindBody = neckDepth != Float.NEGATIVE_INFINITY
                && neckDepth < m.groupDepth[GooseModel.GROUP_BODY];
        int i = 0;
        if (!headBehindBody) groupOrder[i++] = GooseModel.GROUP_BODY;
        groupOrder[i++] = GooseModel.GROUP_NECK;
        groupOrder[i++] = GooseModel.GROUP_HEAD;
        if (headBehindBody) groupOrder[i] = GooseModel.GROUP_BODY;
    }

    private void applyShading(float cx, float cy, float radius) {
        applyShading(cx, cy, radius, bodyColor);
    }

    /** Luz desde arriba a la izquierda, compartida por todo el grupo para que no se noten uniones. */
    private void applyShading(float cx, float cy, float radius, int color) {
        if (bodyShader == null || shaderColor != color) {
            bodyShader = new RadialGradient(0f, 0f, 1f,
                    new int[] {blend(color, Color.WHITE, 0.35f), color, blend(color, INK, 0.16f)},
                    new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            shaderColor = color;
        }
        matrix.setScale(radius * 1.35f, radius * 1.35f);
        matrix.postTranslate(cx - radius * 0.3f, cy - radius * 0.45f);
        bodyShader.setLocalMatrix(matrix);
        shaded.setShader(bodyShader);
    }

    private void drawHeart(Canvas canvas, float x, float y, float size, int color) {
        path.rewind();
        path.moveTo(x, y + size * 0.55f);
        path.cubicTo(x - size, y - size * 0.1f, x - size * 0.45f, y - size * 0.85f, x, y - size * 0.3f);
        path.cubicTo(x + size * 0.45f, y - size * 0.85f, x + size, y - size * 0.1f, x, y + size * 0.55f);
        path.close();
        fill.setShader(null);
        fill.setColor(color);
        canvas.drawPath(path, fill);
    }

    /** Ala de largo 1 y ancho 1, con el frente en +x y la punta hacia atrás. */
    private void buildUnitWing() {
        unitWing.moveTo(0.45f, 0f);
        unitWing.cubicTo(0.45f, -0.5f, -0.1f, -0.55f, -0.58f, -0.18f);
        unitWing.lineTo(-0.5f, 0.05f);
        unitWing.cubicTo(-0.1f, 0.48f, 0.4f, 0.42f, 0.45f, 0f);
        unitWing.close();
        unitFeathers.moveTo(-0.38f, -0.06f);
        unitFeathers.quadTo(-0.02f, 0.06f, 0.25f, -0.02f);
        unitFeathers.moveTo(-0.3f, 0.12f);
        unitFeathers.quadTo(0f, 0.24f, 0.22f, 0.16f);
    }

    static int blend(int from, int to, float t) {
        int a = Color.alpha(from);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }
}
