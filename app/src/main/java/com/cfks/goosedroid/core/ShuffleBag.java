package com.cfks.goosedroid.core;

import java.util.Random;

/**
 * Bolsa de índices 0..n-1 que se reparten en orden aleatorio sin repetir hasta
 * vaciarse; entonces se vuelve a mezclar. Evita rachas largas del mismo valor.
 */
public final class ShuffleBag {
    private final int[] order;
    private final Random random;
    private int cursor;

    public ShuffleBag(int size) {
        this(size, MathUtil.RNG);
    }

    public ShuffleBag(int size, Random random) {
        if (size <= 0) throw new IllegalArgumentException("size debe ser positivo: " + size);
        this.order = new int[size];
        this.random = random;
        for (int i = 0; i < size; i++) order[i] = i;
        shuffle();
    }

    public int next() {
        int value = order[cursor++];
        if (cursor == order.length) shuffle();
        return value;
    }

    private void shuffle() {
        for (int i = order.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
        cursor = 0;
    }
}
