package com.proxyai.proxy;

import com.proxyai.config.AppClock;
import com.proxyai.dto.GenerationRequest;
import com.proxyai.dto.GenerationResponse;
import com.proxyai.exception.RateLimitExceededException;
import com.proxyai.model.Plan;
import com.proxyai.model.UserAccount;
import com.proxyai.service.AIGenerationService;
import com.proxyai.store.UserStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Proxy #1 de la cadena (punto de entrada): limita las requests por minuto segun el plan.
 * Si se supera el limite -> 429 Too Many Requests + header Retry-After.
 * Si pasa, delega en el QuotaProxyService.
 *
 * Cadena: RateLimitProxyService -> QuotaProxyService -> MockAIGenerationService
 */
@Service("rateLimitProxyService")
@Primary
public class RateLimitProxyService implements AIGenerationService {

    private final AIGenerationService next;
    private final UserStore users;
    private final AppClock clock;

    public RateLimitProxyService(@Qualifier("quotaProxyService") AIGenerationService next,
                                 UserStore users, AppClock clock) {
        this.next = next;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public GenerationResponse generate(GenerationRequest request) {
        UserAccount user = users.getOrRecreate(request.userId());
        Plan plan = user.getPlan();

        if (!plan.isUnlimitedRate()) {
            int limit = plan.getRequestsPerMinute();
            // Reserva atomica de un "slot" en la ventana del minuto actual.
            int count = user.getRequestsThisMinute().incrementAndGet();
            if (count > limit) {
                user.getRequestsThisMinute().decrementAndGet();
                throw new RateLimitExceededException(limit, clock.secondsUntilNextMinute());
            }
        } else {
            user.getRequestsThisMinute().incrementAndGet();
        }
        return next.generate(request);
    }
}
