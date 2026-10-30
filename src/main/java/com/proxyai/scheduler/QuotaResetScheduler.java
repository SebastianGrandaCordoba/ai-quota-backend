package com.proxyai.scheduler;

import com.proxyai.config.AppClock;
import com.proxyai.model.UserAccount;
import com.proxyai.security.SessionRateLimiter;
import com.proxyai.store.UserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tareas programadas de reinicio. */
@Component
public class QuotaResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuotaResetScheduler.class);

    private final UserStore users;
    private final SessionRateLimiter sessions;
    private final AppClock clock;

    public QuotaResetScheduler(UserStore users, SessionRateLimiter sessions, AppClock clock) {
        this.users = users;
        this.sessions = sessions;
        this.clock = clock;
    }

    /** Segundo 0 de cada minuto: reinicia el contador de requests por minuto. */
    @Scheduled(cron = "${app.scheduler.rate-limit-cron:0 * * * * *}", zone = "${app.time-zone}")
    public void resetRateLimits() {
        users.all().forEach(u -> u.getRequestsThisMinute().set(0));
        sessions.reset();
    }

    /** 00:00 del dia 1 de cada mes: reinicia la cuota mensual de tokens. */
    @Scheduled(cron = "${app.scheduler.monthly-cron:0 0 0 1 * *}", zone = "${app.time-zone}")
    public void resetMonthlyQuotas() {
        users.all().forEach(UserAccount::resetTokens);
        log.info("Cuotas mensuales reiniciadas para {} usuarios", users.size());
    }

    /** Todos los dias a las 00:05: descarta historial de mas de 31 dias para acotar memoria. */
    @Scheduled(cron = "0 5 0 * * *", zone = "${app.time-zone}")
    public void pruneHistory() {
        var cutoff = clock.today().minusDays(31);
        users.all().forEach(u -> u.pruneHistoryBefore(cutoff));
    }
}
