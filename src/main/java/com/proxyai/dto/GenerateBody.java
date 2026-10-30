package com.proxyai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo aceptado por POST /api/ai/generate. */
public record GenerateBody(
        @NotBlank(message = "El prompt no puede estar vacío")
        @Size(max = 4000, message = "El prompt no puede superar 4000 caracteres")
        String prompt) {
}
