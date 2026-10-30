package com.proxyai.controller;

import com.proxyai.dto.SessionResponse;
import com.proxyai.exception.TooManySessionsException;
import com.proxyai.config.AppClock;
import com.proxyai.model.UserAccount;
import com.proxyai.security.AuthInterceptor;
import com.proxyai.security.SessionRateLimiter;
import com.proxyai.security.TokenService;
import com.proxyai.store.UserStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identidad anonima: el servidor crea el usuario y firma su ID.
 * Si la peticion ya trae un token valido, se reutiliza esa identidad.
 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final TokenService tokens;
    private final UserStore users;
    private final SessionRateLimiter limiter;
    private final AppClock clock;

    public SessionController(TokenService tokens, UserStore users, SessionRateLimiter limiter, AppClock clock) {
        this.tokens = tokens;
        this.users = users;
        this.limiter = limiter;
        this.clock = clock;
    }

    @PostMapping
    public SessionResponse session(HttpServletRequest request) {
        String existing = AuthInterceptor.extractToken(request);
        var verified = tokens.verify(existing);
        if (verified.isPresent()) {
            UserAccount user = users.getOrRecreate(verified.get());
            return new SessionResponse(existing, user.getId(), user.getPlan().name(), false);
        }
        if (!limiter.tryAcquire(clientIp(request))) {
            throw new TooManySessionsException(clock.secondsUntilNextMinute());
        }
        UserAccount user = users.create();
        return new SessionResponse(tokens.issue(user.getId()), user.getId(), user.getPlan().name(), true);
    }

    private static String clientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
