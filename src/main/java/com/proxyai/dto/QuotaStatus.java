package com.proxyai.dto;

import java.time.ZonedDateTime;

public record QuotaStatus(
        String userId,
        String plan,
        long tokensUsed,
        Long tokensRemaining,   // null = ilimitado
        Long tokensLimit,       // null = ilimitado
        boolean unlimited,
        ZonedDateTime resetDate,
        RateLimitStatus rateLimit) {
}
