package com.proxyai.service;

import com.proxyai.dto.GenerationRequest;
import com.proxyai.dto.GenerationResponse;

/**
 * Sujeto del patron Proxy: interfaz comun que implementan el servicio real
 * (MockAIGenerationService) y los proxies (RateLimitProxyService, QuotaProxyService).
 * El cliente (controlador) no sabe si habla con el objeto real o con un proxy.
 */
public interface AIGenerationService {
    GenerationResponse generate(GenerationRequest request);
}
