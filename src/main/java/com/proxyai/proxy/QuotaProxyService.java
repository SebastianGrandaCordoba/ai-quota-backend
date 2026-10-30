package com.proxyai.proxy;

import com.proxyai.config.AppClock;
import com.proxyai.dto.GenerationRequest;
import com.proxyai.dto.GenerationResponse;
import com.proxyai.exception.QuotaExceededException;
import com.proxyai.model.Plan;
import com.proxyai.model.UserAccount;
import com.proxyai.service.AIGenerationService;
import com.proxyai.service.TokenEstimator;
import com.proxyai.store.UserStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Proxy #2 de la cadena: controla la cuota MENSUAL de tokens.
 * - Si la cuota ya esta agotada (o no alcanza para el prompt) -> 402 Payment Required.
 * - Si pasa, delega en el servicio real y descuenta los tokens consumidos (prompt + respuesta).
 */
@Service("quotaProxyService")
public class QuotaProxyService implements AIGenerationService {

    private final AIGenerationService realService;
    private final UserStore users;
    private final AppClock clock;

    public QuotaProxyService(@Qualifier("realAIGenerationService") AIGenerationService realService,
                             UserStore users, AppClock clock) {
        this.realService = realService;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public GenerationResponse generate(GenerationRequest request) {
        UserAccount user = users.getOrRecreate(request.userId());
        Plan plan = user.getPlan();
        int promptTokens = TokenEstimator.estimate(request.prompt());

        if (!plan.isUnlimitedTokens()) {
            long used = user.getTokensUsed();
            long limit = plan.getMonthlyTokens();
            if (used >= limit) {
                throw new QuotaExceededException(
                        "Cuota mensual agotada (" + limit + " tokens del plan " + plan + "). Actualiza tu plan para continuar.",
                        used, limit, promptTokens);
            }
            if (used + promptTokens > limit) {
                throw new QuotaExceededException(
                        "Tokens insuficientes: el prompt requiere ~" + promptTokens + " y quedan " + (limit - used) + ".",
                        used, limit, promptTokens);
            }
        }

        GenerationResponse response = realService.generate(request);
        charge(user, response.totalTokens());
        return response;
    }

    /**
     * Descuenta tokens. Si la respuesta excede lo que quedaba, se cobra solo el remanente
     * (la cuota queda exactamente agotada y nunca se muestra un consumo mayor al limite).
     */
    private void charge(UserAccount user, long tokens) {
        synchronized (user) {
            Plan plan = user.getPlan();
            long charged = tokens;
            if (!plan.isUnlimitedTokens()) {
                long remaining = Math.max(0, plan.getMonthlyTokens() - user.getTokensUsed());
                charged = Math.min(tokens, remaining);
            }
            user.addTokens(charged, clock.today());
        }
    }
}
