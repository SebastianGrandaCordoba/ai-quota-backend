package com.proxyai.dto;

public record SessionResponse(String token, String userId, String plan, boolean created) {
}
