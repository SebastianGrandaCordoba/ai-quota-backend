package com.proxyai.dto;

/** Respuesta de /api/ai/generate: el texto generado + el estado actualizado de cuota. */
public record GenerateResult(GenerationResponse generation, long tokensCharged, QuotaStatus quota) {
}
