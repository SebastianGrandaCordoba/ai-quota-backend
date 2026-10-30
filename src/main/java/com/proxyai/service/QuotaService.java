package com.proxyai.service;

import com.proxyai.config.AppClock;
import com.proxyai.dto.*;
import com.proxyai.exception.InvalidPlanChangeException;
import com.proxyai.exception.PaymentDeclinedException;
import com.proxyai.model.Plan;
import com.proxyai.model.UserAccount;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Consultas de cuota, historial y upgrade de plan (con pago simulado). */
@Service
public class QuotaService {

    /** Tarjeta de prueba que siempre es rechazada, para simular un pago fallido. */
    public static final String DECLINED_TEST_CARD = "4000000000000002";
    private static final Locale ES = Locale.forLanguageTag("es");

    private final AppClock clock;

    public QuotaService(AppClock clock) {
        this.clock = clock;
    }

    public RateLimitStatus rateLimit(UserAccount user) {
        Plan plan = user.getPlan();
        int used = user.getRequestsThisMinute().get();
        Integer limit = plan.isUnlimitedRate() ? null : plan.getRequestsPerMinute();
        Integer remaining = limit == null ? null : Math.max(0, limit - used);
        return new RateLimitStatus(limit, used, remaining, clock.secondsUntilNextMinute(), clock.nextMinuteEpochSeconds());
    }

    public QuotaStatus status(UserAccount user) {
        Plan plan = user.getPlan();
        long used = user.getTokensUsed();
        boolean unlimited = plan.isUnlimitedTokens();
        Long limit = unlimited ? null : plan.getMonthlyTokens();
        Long remaining = unlimited ? null : Math.max(0, plan.getMonthlyTokens() - used);
        return new QuotaStatus(user.getId(), plan.name(), used, remaining, limit, unlimited,
                clock.nextMonthlyReset(), rateLimit(user));
    }

    public HistoryResponse history(UserAccount user, int days) {
        LocalDate today = clock.today();
        List<DailyUsageDto> list = new ArrayList<>(days);
        long totalTokens = 0;
        int totalRequests = 0;
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            UserAccount.DailyUsage u = user.getDaily(d);
            long t = u == null ? 0 : u.getTokens();
            int r = u == null ? 0 : u.getRequests();
            totalTokens += t;
            totalRequests += r;
            String label = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, ES);
            list.add(new DailyUsageDto(d, label, t, r));
        }
        return new HistoryResponse(user.getId(), days, totalTokens, totalRequests, list);
    }

    public UpgradeResponse upgrade(UserAccount user, UpgradeRequest req) {
        Plan target = Plan.valueOf(req.targetPlan());
        Plan current = user.getPlan();
        if (target.ordinal() <= current.ordinal()) {
            throw new InvalidPlanChangeException("No es posible cambiar de " + current + " a " + target + ". Solo se permiten upgrades.");
        }

        String digits = req.cardNumber().replace(" ", "");
        if (digits.length() < 12 || digits.length() > 19 || !luhnValid(digits)) {
            throw new PaymentDeclinedException("Número de tarjeta inválido (no pasa la verificación Luhn).");
        }
        if (expired(req.expiry())) {
            throw new PaymentDeclinedException("La tarjeta está vencida.");
        }
        if (DECLINED_TEST_CARD.equals(digits)) {
            throw new PaymentDeclinedException("Pago rechazado por el emisor (tarjeta de prueba de rechazo).");
        }

        user.setPlan(target);
        return new UpgradeResponse(
                "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                current.name(), target.name(), target.getPriceUsd(),
                digits.substring(digits.length() - 4), Instant.now(), status(user));
    }

    /**
     * Herramienta de demostración: consume tokens sin llamar al modelo, para poder
     * mostrar el agotamiento de cuota (402) sin enviar cientos de prompts.
     * Solo afecta al usuario autenticado y se puede desactivar con APP_DEMO_TOOLS=false.
     */
    public QuotaStatus simulateUsage(UserAccount user, long tokens) {
        synchronized (user) {
            Plan plan = user.getPlan();
            long charge = plan.isUnlimitedTokens() ? tokens
                    : Math.min(tokens, Math.max(0, plan.getMonthlyTokens() - user.getTokensUsed()));
            user.addTokens(charge, clock.today(), false);
        }
        return status(user);
    }

    static boolean luhnValid(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (dbl) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    private boolean expired(String mmYY) {
        int month = Integer.parseInt(mmYY.substring(0, 2));
        int year = 2000 + Integer.parseInt(mmYY.substring(3, 5));
        LocalDate today = clock.today();
        return year < today.getYear() || (year == today.getYear() && month < today.getMonthValue());
    }
}
