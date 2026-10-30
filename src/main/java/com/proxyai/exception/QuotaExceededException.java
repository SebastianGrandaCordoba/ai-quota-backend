package com.proxyai.exception;

/** Se lanza cuando la cuota mensual de tokens esta agotada -> HTTP 402. */
public class QuotaExceededException extends RuntimeException {
    private final long tokensUsed;
    private final long tokensLimit;
    private final long tokensRequired;

    public QuotaExceededException(String message, long tokensUsed, long tokensLimit, long tokensRequired) {
        super(message);
        this.tokensUsed = tokensUsed;
        this.tokensLimit = tokensLimit;
        this.tokensRequired = tokensRequired;
    }

    public long getTokensUsed() { return tokensUsed; }
    public long getTokensLimit() { return tokensLimit; }
    public long getTokensRequired() { return tokensRequired; }
}
