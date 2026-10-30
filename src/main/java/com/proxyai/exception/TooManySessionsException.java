package com.proxyai.exception;

public class TooManySessionsException extends RuntimeException {
    private final long retryAfterSeconds;
    public TooManySessionsException(long retryAfterSeconds) {
        super("Demasiadas sesiones creadas desde esta dirección. Intenta de nuevo en " + retryAfterSeconds + " s.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
