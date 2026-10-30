package com.proxyai.security;

import com.proxyai.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Exige "Authorization: Bearer <token>" firmado en /api/ai/** y /api/quota/**. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ATTR = "authenticatedUserId";

    private final TokenService tokens;

    public AuthInterceptor(TokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true; // preflight CORS
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new UnauthorizedException("Falta el token de sesión. Llama primero a POST /api/session.");
        }
        String userId = tokens.verify(header.substring(7).trim())
                .orElseThrow(() -> new UnauthorizedException("Token de sesión inválido."));
        request.setAttribute(USER_ATTR, userId);
        return true;
    }

    public static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
    }
}
