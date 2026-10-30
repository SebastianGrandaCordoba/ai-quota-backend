package com.proxyai.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limita la creacion de sesiones anonimas por IP (evita evadir las cuotas
 * creando usuarios nuevos sin parar). La ventana se reinicia cada minuto
 * desde el scheduler.
 */
@Component
public class SessionRateLimiter {

    public static final int MAX_SESSIONS_PER_MINUTE = 20;

    private final ConcurrentHashMap<String, AtomicInteger> perIp = new ConcurrentHashMap<>();

    public boolean tryAcquire(String ip) {
        return perIp.computeIfAbsent(ip, k -> new AtomicInteger()).incrementAndGet() <= MAX_SESSIONS_PER_MINUTE;
    }

    public void reset() {
        perIp.clear();
    }
}
