package com.proxyai.dto;

import java.util.List;

public record HistoryResponse(String userId, int days, long totalTokens, int totalRequests, List<DailyUsageDto> history) {
}
