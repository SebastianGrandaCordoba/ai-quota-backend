package com.proxyai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Emite y verifica tokens de sesion firmados con HMAC-SHA256: "<userId>.<firma>".
 * El backend solo acepta identidades que el mismo firmo, por lo que un navegador
 * no puede suplantar a otro usuario enviando un ID arbitrario.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final byte[] secret;

    public TokenService(@Value("${app.security.token-secret:}") String configured) {
        if (configured == null || configured.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
            log.warn("APP_TOKEN_SECRET no definido: se uso un secreto aleatorio; los tokens dejaran de ser validos al reiniciar.");
        } else {
            if (configured.length() < 32) {
                log.warn("APP_TOKEN_SECRET tiene menos de 32 caracteres; se recomienda un valor más largo.");
            }
            this.secret = configured.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String issue(String userId) {
        return userId + "." + sign(userId);
    }

    public Optional<String> verify(String token) {
        if (token == null) return Optional.empty();
        int dot = token.lastIndexOf('.');
        if (dot <= 0 || dot == token.length() - 1) return Optional.empty();
        String userId = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        if (!userId.matches("usr_[a-f0-9]{16}")) return Optional.empty();
        boolean ok = MessageDigest.isEqual(
                sign(userId).getBytes(StandardCharsets.US_ASCII),
                sig.getBytes(StandardCharsets.US_ASCII));
        return ok ? Optional.of(userId) : Optional.empty();
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }
}
