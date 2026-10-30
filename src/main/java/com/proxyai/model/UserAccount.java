package com.proxyai.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Estado de un usuario. Las operaciones que modifican varios campos a la vez
 * se sincronizan sobre la propia instancia.
 */
public class UserAccount {

    private final String id;
    private final Instant createdAt;
    private volatile Plan plan = Plan.FREE;

    /** Requests realizadas en la ventana del minuto actual. */
    private final AtomicInteger requestsThisMinute = new AtomicInteger();

    /** Tokens consumidos en el ciclo mensual actual. */
    private long tokensUsed;

    /** Historial diario: fecha -> uso. */
    private final Map<LocalDate, DailyUsage> daily = new ConcurrentHashMap<>();

    public UserAccount(String id) {
        this.id = id;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }
    public AtomicInteger getRequestsThisMinute() { return requestsThisMinute; }

    public synchronized long getTokensUsed() { return tokensUsed; }
    public synchronized void resetTokens() { tokensUsed = 0; }

    public synchronized void addTokens(long tokens, LocalDate day) {
        addTokens(tokens, day, true);
    }

    public synchronized void addTokens(long tokens, LocalDate day, boolean countRequest) {
        tokensUsed += tokens;
        daily.computeIfAbsent(day, d -> new DailyUsage()).add(tokens, countRequest);
    }

    public DailyUsage getDaily(LocalDate day) {
        return daily.get(day);
    }

    public void pruneHistoryBefore(LocalDate cutoff) {
        daily.keySet().removeIf(d -> d.isBefore(cutoff));
    }

    public static class DailyUsage {
        private long tokens;
        private int requests;

        synchronized void add(long t, boolean countRequest) {
            tokens += t;
            if (countRequest) requests++;
        }

        public synchronized long getTokens() { return tokens; }
        public synchronized int getRequests() { return requests; }
    }
}
