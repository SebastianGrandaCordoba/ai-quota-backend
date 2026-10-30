package com.proxyai.controller;

import com.proxyai.dto.*;
import com.proxyai.model.UserAccount;
import com.proxyai.security.AuthInterceptor;
import com.proxyai.service.AIGenerationService;
import com.proxyai.service.QuotaService;
import com.proxyai.store.UserStore;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Cliente del patron Proxy. Recibe un AIGenerationService (@Primary = RateLimitProxyService)
 * y no sabe que detras hay una cadena RateLimit -> Quota -> Mock.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AIGenerationService aiService;
    private final QuotaService quotaService;
    private final UserStore users;

    public AiController(AIGenerationService aiService, QuotaService quotaService, UserStore users) {
        this.aiService = aiService;
        this.quotaService = quotaService;
        this.users = users;
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateResult> generate(@RequestAttribute(AuthInterceptor.USER_ATTR) String userId,
                                                   @Valid @RequestBody GenerateBody body) {
        UserAccount user = users.getOrRecreate(userId);
        long before = user.getTokensUsed();
        GenerationResponse generation = aiService.generate(new GenerationRequest(userId, body.prompt().trim()));
        long charged = user.getTokensUsed() - before;
        QuotaStatus quota = quotaService.status(user);
        RateLimitStatus rl = quota.rateLimit();

        var response = ResponseEntity.ok();
        if (rl.limit() != null) {
            response.header("X-RateLimit-Limit", String.valueOf(rl.limit()))
                    .header("X-RateLimit-Remaining", String.valueOf(rl.remaining()))
                    .header("X-RateLimit-Reset", String.valueOf(rl.resetAtEpochSeconds()));
        }
        return response.body(new GenerateResult(generation, Math.max(0, charged), quota));
    }
}
