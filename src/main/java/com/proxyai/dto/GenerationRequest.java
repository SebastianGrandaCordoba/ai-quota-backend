package com.proxyai.dto;

/**
 * Request que viaja por la cadena de proxies. El userId lo fija el servidor
 * a partir del token verificado; nunca se toma del cuerpo enviado por el navegador.
 */
public record GenerationRequest(String userId, String prompt) {
}
