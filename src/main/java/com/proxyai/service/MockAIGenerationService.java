package com.proxyai.service;

import com.proxyai.dto.GenerationRequest;
import com.proxyai.dto.GenerationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Objeto REAL del patron Proxy. Simula un modelo de generacion de texto:
 * espera ~1200 ms (Thread.sleep) y devuelve un texto aleatorio de un conjunto predefinido.
 * No conoce nada de cuotas ni de limites: esa responsabilidad es de los proxies.
 */
@Service("realAIGenerationService")
public class MockAIGenerationService implements AIGenerationService {

    public static final String MODEL = "mock-gpt-1";

    static final List<String> RESPONSES = List.of(
            "Una buena respuesta empieza por entender la pregunta. Sobre «%s», lo esencial es separar el problema en partes pequeñas, validar cada supuesto con datos y documentar las decisiones. Así el resultado es fácil de revisar y de mejorar con el tiempo.",
            "Pensemos en «%s» como un sistema: entradas, reglas y salidas. Si las reglas viven en un solo lugar, cambiar el comportamiento no obliga a tocar a quién lo usa. Es la misma idea del patrón Proxy: un intermediario que controla el acceso sin alterar la interfaz.",
            "Respuesta breve sobre «%s»: prioriza la claridad sobre la astucia. Un diseño simple, con responsabilidades bien definidas y pruebas que expliquen la intención, suele envejecer mejor que una solución ingeniosa pero opaca.",
            "Tres ideas para «%s». Primero, define el resultado que esperas y cómo lo vas a medir. Segundo, empieza con la versión más pequeña que funcione de punta a punta. Tercero, itera con retroalimentación real en lugar de suposiciones.",
            "Sobre «%s»: los límites no son un obstáculo sino un contrato. Un plan con cuotas claras permite predecir costos, proteger el servicio frente a abusos y ofrecer una experiencia justa a todos los usuarios que lo comparten.",
            "Si tuviera que resumir «%s» en una frase: control antes que velocidad. Verificar permisos, límites y consumo antes de llamar al recurso costoso evita sorpresas y hace que cada request cuente.",
            "Una perspectiva distinta sobre «%s». Imagina un conserje en la entrada de un edificio: no construye los apartamentos, pero decide quién entra, cuántas veces y lleva el registro. Ese intermediario es exactamente lo que hace un proxy frente a un servicio real."
    );

    private final long delayMs;

    public MockAIGenerationService(@Value("${app.ai.mock-delay-ms:1200}") long delayMs) {
        this.delayMs = delayMs;
    }

    @Override
    public GenerationResponse generate(GenerationRequest request) {
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Generación interrumpida", e);
        }
        String template = RESPONSES.get(ThreadLocalRandom.current().nextInt(RESPONSES.size()));
        String text = template.formatted(topicOf(request.prompt()));

        int promptTokens = TokenEstimator.estimate(request.prompt());
        int completionTokens = TokenEstimator.estimate(text);
        return new GenerationResponse(
                "gen_" + UUID.randomUUID().toString().substring(0, 8),
                text,
                MODEL,
                promptTokens,
                completionTokens,
                promptTokens + completionTokens,
                System.currentTimeMillis() - start,
                Instant.now());
    }

    /** Extrae un "tema" corto del prompt para que la respuesta se sienta contextual. */
    static String topicOf(String prompt) {
        String clean = prompt == null ? "" : prompt.trim().replaceAll("\\s+", " ");
        if (clean.length() <= 60) return clean;
        int cut = clean.lastIndexOf(' ', 60);
        return clean.substring(0, cut > 20 ? cut : 60) + "…";
    }

    /** Promedio de tokens de respuesta (sin el tema), usado por el estimador del frontend. */
    public static int averageCompletionTokens() {
        return (int) Math.round(RESPONSES.stream()
                .mapToInt(r -> TokenEstimator.estimate(r.formatted("")))
                .average().orElse(0)) + 6;
    }
}
