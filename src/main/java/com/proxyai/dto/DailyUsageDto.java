package com.proxyai.dto;

import java.time.LocalDate;

public record DailyUsageDto(LocalDate date, String dayLabel, long tokens, int requests) {
}
