package com.proxyai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proxyai.model.Plan;
import com.proxyai.scheduler.QuotaResetScheduler;
import com.proxyai.store.UserStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserStore users;
    @Autowired QuotaResetScheduler scheduler;

    private static final java.util.concurrent.atomic.AtomicInteger IP = new java.util.concurrent.atomic.AtomicInteger(1);

    private JsonNode newSession() throws Exception {
        String body = mvc.perform(post("/api/session").header("X-Forwarded-For", "10.0.0." + IP.getAndIncrement()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private ResultActions generate(String token, String prompt) throws Exception {
        return mvc.perform(post("/api/ai/generate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("prompt", prompt))));
    }

    @Test
    void rejectsMissingAndForgedTokens() throws Exception {
        mvc.perform(get("/api/quota/status")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/quota/status").header("Authorization", "Bearer usr_0123456789abcdef.forged"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generateChargesTokensAndUpdatesStatus() throws Exception {
        String token = newSession().get("token").asText();
        generate(token, "Explica el patron proxy")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generation.text").isNotEmpty())
                .andExpect(jsonPath("$.tokensCharged").isNumber())
                .andExpect(jsonPath("$.quota.plan").value("FREE"))
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().string("X-RateLimit-Remaining", "9"));

        mvc.perform(get("/api/quota/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokensLimit").value(50000))
                .andExpect(jsonPath("$.tokensUsed").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.resetDate").exists())
                .andExpect(jsonPath("$.rateLimit.used").value(1));
    }

    @Test
    void validatesPrompt() throws Exception {
        String token = newSession().get("token").asText();
        generate(token, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        generate(token, "x".repeat(4001)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/generate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void freePlanReturns429AfterTenRequestsPerMinute() throws Exception {
        String token = newSession().get("token").asText();
        for (int i = 0; i < 10; i++) generate(token, "hola " + i).andExpect(status().isOk());
        generate(token, "uno mas")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));

        scheduler.resetRateLimits();
        generate(token, "despues del reset").andExpect(status().isOk());
    }

    @Test
    void returns402WhenMonthlyQuotaIsExhaustedAndResetsMonthly() throws Exception {
        JsonNode s = newSession();
        String token = s.get("token").asText();
        var user = users.getOrRecreate(s.get("userId").asText());
        user.addTokens(50_000, java.time.LocalDate.now());

        generate(token, "sin cuota")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));

        scheduler.resetMonthlyQuotas();
        assertThat(user.getTokensUsed()).isZero();
        generate(token, "con cuota renovada").andExpect(status().isOk());
    }

    @Test
    void rateLimitIsCheckedBeforeQuota() throws Exception {
        JsonNode s = newSession();
        String token = s.get("token").asText();
        var user = users.getOrRecreate(s.get("userId").asText());
        user.addTokens(50_000, java.time.LocalDate.now());
        for (int i = 0; i < 10; i++) generate(token, "x").andExpect(status().isPaymentRequired());
        // 11a request: la bloquea el primer proxy (RateLimit) antes de llegar a Quota
        generate(token, "x").andExpect(status().isTooManyRequests());
    }

    @Test
    void historyReturnsSevenDays() throws Exception {
        String token = newSession().get("token").asText();
        generate(token, "registro").andExpect(status().isOk());
        mvc.perform(get("/api/quota/history").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(7))
                .andExpect(jsonPath("$.history[6].requests").value(1))
                .andExpect(jsonPath("$.totalRequests").value(1));
    }

    @Test
    void upgradeFlow() throws Exception {
        JsonNode s = newSession();
        String token = s.get("token").asText();
        String ok = """
                {"targetPlan":"PRO","cardHolder":"Ada Lovelace","cardNumber":"4242 4242 4242 4242","expiry":"12/30","cvc":"123"}""";
        String declined = ok.replace("4242 4242 4242 4242", "4000 0000 0000 0002");
        String badLuhn = ok.replace("4242 4242 4242 4242", "4242 4242 4242 4241");
        String invalid = ok.replace("\"PRO\"", "\"GOLD\"");

        mvc.perform(post("/api/quota/upgrade").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/quota/upgrade").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(badLuhn)).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"));
        mvc.perform(post("/api/quota/upgrade").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(declined)).andExpect(status().isPaymentRequired());
        mvc.perform(post("/api/quota/upgrade").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.cardLast4").value("4242"))
                .andExpect(jsonPath("$.quota.tokensLimit").value(500000))
                .andExpect(jsonPath("$.quota.rateLimit.limit").value(60));
        mvc.perform(post("/api/quota/upgrade").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(ok)).andExpect(status().isConflict());

        assertThat(users.getOrRecreate(s.get("userId").asText()).getPlan()).isEqualTo(Plan.PRO);
    }

    @Test
    void enterpriseIsUnlimited() throws Exception {
        JsonNode s = newSession();
        String token = s.get("token").asText();
        users.getOrRecreate(s.get("userId").asText()).setPlan(Plan.ENTERPRISE);
        for (int i = 0; i < 70; i++) generate(token, "ilimitado").andExpect(status().isOk());
        mvc.perform(get("/api/quota/status").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.unlimited").value(true))
                .andExpect(jsonPath("$.tokensRemaining").doesNotExist());
    }

    @Test
    void sessionReusesValidToken() throws Exception {
        JsonNode s = newSession();
        mvc.perform(post("/api/session").header("Authorization", "Bearer " + s.get("token").asText()))
                .andExpect(jsonPath("$.userId").value(s.get("userId").asText()))
                .andExpect(jsonPath("$.created").value(false));
    }

    @Test
    void corsOnlyAllowsConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/ai/generate")
                        .header("Origin", "https://frontend.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.com"));
        mvc.perform(options("/api/ai/generate")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void simulateUsageExhaustsQuotaThen402() throws Exception {
        String token = newSession().get("token").asText();
        mvc.perform(post("/api/quota/simulate-usage").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tokens\":999999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokensUsed").value(50000))
                .andExpect(jsonPath("$.tokensRemaining").value(0));
        generate(token, "hola").andExpect(status().isPaymentRequired());
        mvc.perform(post("/api/quota/simulate-usage").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tokens\":0}"))
                .andExpect(status().isBadRequest());
    }
}
