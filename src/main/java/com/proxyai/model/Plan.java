package com.proxyai.model;

/**
 * Planes disponibles. Un valor de -1 significa "ilimitado".
 */
public enum Plan {
    FREE(10, 50_000L, 0),
    PRO(60, 500_000L, 19),
    ENTERPRISE(-1, -1L, 199);

    private final int requestsPerMinute;
    private final long monthlyTokens;
    private final int priceUsd;

    Plan(int requestsPerMinute, long monthlyTokens, int priceUsd) {
        this.requestsPerMinute = requestsPerMinute;
        this.monthlyTokens = monthlyTokens;
        this.priceUsd = priceUsd;
    }

    public int getRequestsPerMinute() { return requestsPerMinute; }
    public long getMonthlyTokens() { return monthlyTokens; }
    public int getPriceUsd() { return priceUsd; }
    public boolean isUnlimitedRate() { return requestsPerMinute < 0; }
    public boolean isUnlimitedTokens() { return monthlyTokens < 0; }
}
