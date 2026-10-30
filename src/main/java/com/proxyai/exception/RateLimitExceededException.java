package com.proxyai.exception;

/** Se lanza cuando el usuario supera N requests por minuto -> HTTP 429. */
public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;
    private final int limit;

    public RateLimitExceededException(int limit, long retryAfterSeconds) {
        super("Límite de " + limit + " requests por minuto alcanzado. Intenta de nuevo en " + retryAfterSeconds + " s.");
        this.limit = limit;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
    public int getLimit() { return limit; }
}
