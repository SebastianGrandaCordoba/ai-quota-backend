package com.proxyai.dto;

import java.time.Instant;

public record UpgradeResponse(
        String transactionId,
        String previousPlan,
        String plan,
        int amountUsd,
        String cardLast4,
        Instant processedAt,
        QuotaStatus quota) {
}
