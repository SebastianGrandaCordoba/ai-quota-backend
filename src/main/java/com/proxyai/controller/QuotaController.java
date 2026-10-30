package com.proxyai.controller;

import com.proxyai.dto.HistoryResponse;
import com.proxyai.dto.QuotaStatus;
import com.proxyai.dto.UpgradeRequest;
import com.proxyai.dto.UpgradeResponse;
import com.proxyai.security.AuthInterceptor;
import com.proxyai.service.QuotaService;
import com.proxyai.store.UserStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quota")
public class QuotaController {

    private final QuotaService quotaService;
    private final UserStore users;

    private final boolean demoTools;

    public QuotaController(QuotaService quotaService, UserStore users,
                           @Value("${app.demo-tools:true}") boolean demoTools) {
        this.quotaService = quotaService;
        this.users = users;
        this.demoTools = demoTools;
    }

    public record SimulateUsageBody(
            @Min(value = 1, message = "tokens debe ser al menos 1")
            @Max(value = 1_000_000, message = "tokens no puede superar 1.000.000")
            long tokens) {
    }

    /** Solo para demostración (APP_DEMO_TOOLS). Ver README. */
    @PostMapping("/simulate-usage")
    public QuotaStatus simulateUsage(@RequestAttribute(AuthInterceptor.USER_ATTR) String userId,
                                     @Valid @RequestBody SimulateUsageBody body) {
        if (!demoTools) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return quotaService.simulateUsage(users.getOrRecreate(userId), body.tokens());
    }

    @GetMapping("/status")
    public QuotaStatus status(@RequestAttribute(AuthInterceptor.USER_ATTR) String userId) {
        return quotaService.status(users.getOrRecreate(userId));
    }

    @GetMapping("/history")
    public HistoryResponse history(@RequestAttribute(AuthInterceptor.USER_ATTR) String userId) {
        return quotaService.history(users.getOrRecreate(userId), 7);
    }

    @PostMapping("/upgrade")
    public UpgradeResponse upgrade(@RequestAttribute(AuthInterceptor.USER_ATTR) String userId,
                                   @Valid @RequestBody UpgradeRequest body) {
        return quotaService.upgrade(users.getOrRecreate(userId), body);
    }
}
