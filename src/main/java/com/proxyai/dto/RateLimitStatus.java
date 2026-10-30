package com.proxyai.dto;

public record RateLimitStatus(
        Integer limit,        // null = ilimitado
        int used,
        Integer remaining,    // null = ilimitado
        long resetInSeconds,
        long resetAtEpochSeconds) {
}
