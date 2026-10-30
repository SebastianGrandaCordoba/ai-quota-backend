package com.proxyai;

import com.proxyai.config.AppClock;
import com.proxyai.dto.GenerationRequest;
import com.proxyai.dto.GenerationResponse;
import com.proxyai.exception.RateLimitExceededException;
import com.proxyai.proxy.QuotaProxyService;
import com.proxyai.proxy.RateLimitProxyService;
import com.proxyai.service.AIGenerationService;
import com.proxyai.service.MockAIGenerationService;
import com.proxyai.store.UserStore;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxyChainUnitTest {

    @Test
    void realServiceSimulatesLatency() {
        var real = new MockAIGenerationService(1200);
        long t = System.currentTimeMillis();
        GenerationResponse r = real.generate(new GenerationRequest("usr_0000000000000000", "hola mundo"));
        assertThat(System.currentTimeMillis() - t).isGreaterThanOrEqualTo(1150);
        assertThat(r.text()).contains("hola mundo");
        assertThat(r.totalTokens()).isEqualTo(r.promptTokens() + r.completionTokens());
    }

    @Test
    void rateLimitProxyDoesNotReachInnerServiceWhenBlocked() {
        var store = new UserStore();
        var clock = new AppClock("America/Bogota");
        AtomicInteger innerCalls = new AtomicInteger();
        AIGenerationService counting = req -> {
            innerCalls.incrementAndGet();
            return new MockAIGenerationService(0).generate(req);
        };
        var quota = new QuotaProxyService(counting, store, clock);
        var rate = new RateLimitProxyService(quota, store, clock);
        var req = new GenerationRequest("usr_aaaaaaaaaaaaaaaa", "prueba");

        for (int i = 0; i < 10; i++) rate.generate(req);
        assertThatThrownBy(() -> rate.generate(req))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(e -> assertThat(((RateLimitExceededException) e).getRetryAfterSeconds()).isBetween(1L, 60L));
        assertThat(innerCalls.get()).isEqualTo(10);
        assertThat(store.getOrRecreate("usr_aaaaaaaaaaaaaaaa").getTokensUsed()).isPositive();
    }
}
