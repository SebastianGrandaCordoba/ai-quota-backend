package com.proxyai.controller;

import com.proxyai.model.Plan;
import com.proxyai.service.MockAIGenerationService;
import com.proxyai.service.TokenEstimator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Endpoints publicos informativos. */
@RestController
public class MetaController {

    @GetMapping("/")
    public Map<String, Object> root() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service", "ai-quota-backend");
        m.put("status", "ok");
        m.put("chain", "RateLimitProxyService -> QuotaProxyService -> MockAIGenerationService");
        m.put("endpoints", List.of("POST /api/session", "POST /api/ai/generate", "GET /api/quota/status",
                "GET /api/quota/history", "POST /api/quota/upgrade", "GET /api/plans", "GET /actuator/health"));
        return m;
    }

    @GetMapping("/api/plans")
    public Map<String, Object> plans() {
        List<Map<String, Object>> plans = Arrays.stream(Plan.values()).map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.name());
            m.put("requestsPerMinute", p.isUnlimitedRate() ? null : p.getRequestsPerMinute());
            m.put("monthlyTokens", p.isUnlimitedTokens() ? null : p.getMonthlyTokens());
            m.put("priceUsd", p.getPriceUsd());
            return m;
        }).toList();
        Map<String, Object> estimator = new LinkedHashMap<>();
        estimator.put("charsPerToken", TokenEstimator.CHARS_PER_TOKEN);
        estimator.put("avgCompletionTokens", MockAIGenerationService.averageCompletionTokens());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plans", plans);
        out.put("estimator", estimator);
        return out;
    }
}
