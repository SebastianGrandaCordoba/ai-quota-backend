package com.proxyai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos de pago SIMULADOS. Nunca se almacenan ni se registran en logs;
 * solo se validan y se conservan los ultimos 4 digitos en la respuesta.
 */
public record UpgradeRequest(
        @NotBlank(message = "targetPlan es obligatorio")
        @Pattern(regexp = "PRO|ENTERPRISE", message = "targetPlan debe ser PRO o ENTERPRISE")
        String targetPlan,
        @NotBlank(message = "El titular es obligatorio")
        @Size(max = 80, message = "El titular no puede superar 80 caracteres")
        String cardHolder,
        @NotBlank(message = "El número de tarjeta es obligatorio")
        @Pattern(regexp = "[0-9 ]{12,23}", message = "Número de tarjeta inválido")
        String cardNumber,
        @NotBlank(message = "La fecha de expiración es obligatoria")
        @Pattern(regexp = "(0[1-9]|1[0-2])/[0-9]{2}", message = "La expiración debe tener formato MM/AA")
        String expiry,
        @NotBlank(message = "El CVC es obligatorio")
        @Pattern(regexp = "[0-9]{3,4}", message = "El CVC debe tener 3 o 4 dígitos")
        String cvc) {
}
