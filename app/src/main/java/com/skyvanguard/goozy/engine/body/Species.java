package com.skyvanguard.goozy.engine.body;

/**
 * Forma del cuerpo. Las medidas están en unidades de modelo: se multiplican
 * por la escala del ganso al proyectarlas a la pantalla.
 */
public enum Species {
    //         cuerpo  cuello  cabeza  pico    pico   cadera  pie
    //         radio   largo   radio   largo   ancho  alto    lado
    GOOSE(     8.2f,   1.0f,   5.2f,   5.8f,   3.4f,  5.0f,   3.4f),
    DUCK(      8.4f,   0.55f,  5.4f,   4.6f,   4.0f,  4.6f,   3.6f),
    CHICK(     7.4f,   0f,     5.8f,   3.0f,   2.0f,  3.2f,   2.6f),
    EGG(       7.0f,   0f,     0f,     0f,     0f,    0f,     0f);

    public final float bodyRadius;
    /** Largo del cuello relativo al ganso; 0 = la cabeza va pegada al cuerpo. */
    public final float neckLength;
    public final float headRadius;
    public final float beakLength;
    public final float beakWidth;
    public final float hipHeight;
    public final float footSide;

    Species(float bodyRadius, float neckLength, float headRadius, float beakLength,
            float beakWidth, float hipHeight, float footSide) {
        this.bodyRadius = bodyRadius;
        this.neckLength = neckLength;
        this.headRadius = headRadius;
        this.beakLength = beakLength;
        this.beakWidth = beakWidth;
        this.hipHeight = hipHeight;
        this.footSide = footSide;
    }

    public boolean hasNeck() {
        return neckLength > 0f;
    }
}
