package com.proxyai.exception;

import com.proxyai.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Formato de error uniforme para toda la API. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> rateLimit(RateLimitExceededException ex, HttpServletRequest req) {
        long resetEpoch = Instant.now().getEpochSecond() + ex.getRetryAfterSeconds();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .header("X-RateLimit-Limit", String.valueOf(ex.getLimit()))
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", String.valueOf(resetEpoch))
                .body(body(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", ex.getMessage(), req,
                        Map.of("retryAfterSeconds", ex.getRetryAfterSeconds(), "limit", ex.getLimit())));
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ApiError> quota(QuotaExceededException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(body(HttpStatus.PAYMENT_REQUIRED, "QUOTA_EXCEEDED", ex.getMessage(), req,
                        Map.of("tokensUsed", ex.getTokensUsed(), "tokensLimit", ex.getTokensLimit(),
                                "tokensRequired", ex.getTokensRequired())));
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    public ResponseEntity<ApiError> declined(PaymentDeclinedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(body(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_DECLINED", ex.getMessage(), req, null));
    }

    @ExceptionHandler(InvalidPlanChangeException.class)
    public ResponseEntity<ApiError> planChange(InvalidPlanChangeException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body(HttpStatus.CONFLICT, "INVALID_PLAN_CHANGE", ex.getMessage(), req, null));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> unauthorized(UnauthorizedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(body(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), req, null));
    }

    @ExceptionHandler(TooManySessionsException.class)
    public ResponseEntity<ApiError> sessions(TooManySessionsException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(body(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_SESSIONS", ex.getMessage(), req,
                        Map.of("retryAfterSeconds", ex.getRetryAfterSeconds())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        String msg = fields.values().stream().findFirst().map(Object::toString).orElse("Solicitud inválida");
        return ResponseEntity.badRequest()
                .body(body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", msg, req, Map.of("fields", fields)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(body(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "El cuerpo de la solicitud no es un JSON válido.", req, null));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Usa Content-Type: application/json.", req, null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(body(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), req, null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> notFound(NoResourceFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(body(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso no encontrado.", req, null));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(org.springframework.web.server.ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus st = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(st).body(body(st, st.name(), ex.getReason() != null ? ex.getReason() : st.getReasonPhrase(), req, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno del servidor.", req, null));
    }

    private ApiError body(HttpStatus status, String code, String message, HttpServletRequest req, Map<String, Object> details) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, req.getRequestURI(), details);
    }
}
