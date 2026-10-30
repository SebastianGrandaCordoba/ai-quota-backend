package com.proxyai.dto;

import java.time.Instant;

public record GenerationResponse(
        String id,
        String text,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs,
        Instant createdAt) {
}
