package com.proxyai.service;

/**
 * Heuristica de tokens (~4 caracteres por token, similar a los tokenizadores BPE en ingles/espanol).
 * El frontend usa exactamente la misma formula para el estimador previo al envio.
 */
public final class TokenEstimator {
    public static final int CHARS_PER_TOKEN = 4;

    private TokenEstimator() {}

    public static int estimate(String text) {
        if (text == null || text.isBlank()) return 0;
        return Math.max(1, (int) Math.ceil(text.trim().length() / (double) CHARS_PER_TOKEN));
    }
}
