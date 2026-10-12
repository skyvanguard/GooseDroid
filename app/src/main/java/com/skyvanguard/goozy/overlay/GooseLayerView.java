package com.skyvanguard.goozy.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.skyvanguard.goozy.engine.GooseRenderer;
import com.skyvanguard.goozy.engine.TheGoose;
import com.skyvanguard.goozy.core.Vec2;

/**
 * Una de las dos capas del overlay.
 *
 * - WORLD: pantalla completa, no táctil. Efectos, huellas, texto.
 * - GOOSE: ventana chica que sigue al ganso. Dibuja el cuerpo y recibe los toques.
 *
 * Las dos dibujan en coordenadas de mundo: pantalla = origen del mundo + mundo × escala.
 */
public class GooseLayerView extends View {
    private static final String TAG = "GooseLayerView";
    private static final int LOOP_ERROR_LOG_INTERVAL = 300;
    private static final int NO_POINTER = -1;

    /** Avisos hacia el servicio dueño de las ventanas. */
    public interface Host {
        /** Origen del mundo en coordenadas de pantalla. */
        int getWorldOriginX();

        int getWorldOriginY();

        /** La capa WORLD cambió de tamaño (rotación, primer layout). */
        void onWorldLayerSizeChanged(int widthPx, int heightPx);
    }

    private final GooseRenderer.Layer layer;
    private final Host host;
    private final int[] locationOnScreen = new int[2];
    private int activePointerId = NO_POINTER;
    private int loopErrorCount = 0;

    public GooseLayerView(Context context, GooseRenderer.Layer layer, Host host) {
        super(context);
        this.layer = layer;
        this.host = host;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (layer == GooseRenderer.Layer.WORLD) {
            host.onWorldLayerSizeChanged(w, h);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!TheGoose.isRunning()) return;

        // Se dibuja relativo a la posición real de la ventana, no a la pedida:
        // así un reposicionamiento que llega un frame tarde no mueve al ganso.
        getLocationOnScreen(locationOnScreen);
        float scale = TheGoose.WorldScale;

        int saveCount = canvas.save();
        try {
            canvas.translate(host.getWorldOriginX() - locationOnScreen[0],
                    host.getWorldOriginY() - locationOnScreen[1]);
            canvas.scale(scale, scale);
            TheGoose.Render(canvas, layer);
        } catch (RuntimeException e) {
            logLoopError("Error during render (" + layer + ")", e);
        } finally {
            canvas.restoreToCount(saveCount);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!TheGoose.isRunning()) return false;

        try {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    return handleDown(event);
                case MotionEvent.ACTION_MOVE:
                    return handleMove(event);
                case MotionEvent.ACTION_POINTER_UP:
                    if (event.getPointerId(event.getActionIndex()) == activePointerId) {
                        finishTouch(event, event.getActionIndex());
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (activePointerId != NO_POINTER) {
                        finishTouch(event, event.findPointerIndex(activePointerId));
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    activePointerId = NO_POINTER;
                    TheGoose.onTouchCancel();
                    return true;
                default:
                    return activePointerId != NO_POINTER;
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Error handling touch event", e);
            activePointerId = NO_POINTER;
            TheGoose.onTouchCancel();
            return false;
        }
    }

    private boolean handleDown(MotionEvent event) {
        float worldX = toWorldX(event, 0);
        float worldY = toWorldY(event, 0);

        boolean isOnGoose = false;
        Vec2 goosePos = TheGoose.getGoosePos();
        if (goosePos != null) {
            float dx = worldX - goosePos.x;
            float dy = worldY - goosePos.y;
            float radius = TheGoose.getTouchRadius();
            isOnGoose = dx * dx + dy * dy <= radius * radius;
        }
        if (!isOnGoose && !TheGoose.isMiniGamePlaying()) {
            return false;
        }

        activePointerId = event.getPointerId(0);
        TheGoose.onTouchStart(worldX, worldY);
        return true;
    }

    private boolean handleMove(MotionEvent event) {
        if (activePointerId == NO_POINTER) return false;
        int index = event.findPointerIndex(activePointerId);
        if (index < 0) return false;
        TheGoose.onTouchMove(toWorldX(event, index), toWorldY(event, index));
        return true;
    }

    private void finishTouch(MotionEvent event, int index) {
        activePointerId = NO_POINTER;
        if (index < 0) {
            TheGoose.onTouchCancel();
            return;
        }
        TheGoose.onTouchEnd(toWorldX(event, index), toWorldY(event, index));
    }

    /**
     * Coordenadas de pantalla del puntero. La ventana se mueve mientras se arrastra,
     * así que se usa el desplazamiento raw del propio evento y no la posición
     * actual de la vista.
     */
    private float toWorldX(MotionEvent event, int index) {
        float screenX = event.getX(index) + (event.getRawX() - event.getX());
        return (screenX - host.getWorldOriginX()) / TheGoose.WorldScale;
    }

    private float toWorldY(MotionEvent event, int index) {
        float screenY = event.getY(index) + (event.getRawY() - event.getY());
        return (screenY - host.getWorldOriginY()) / TheGoose.WorldScale;
    }

    private void logLoopError(String message, RuntimeException e) {
        loopErrorCount++;
        if (loopErrorCount == 1 || loopErrorCount % LOOP_ERROR_LOG_INTERVAL == 0) {
            Log.e(TAG, message + " (x" + loopErrorCount + ")", e);
        }
    }
}
