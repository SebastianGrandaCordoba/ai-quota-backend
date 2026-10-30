package com.proxyai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

/** Utilidades de tiempo centralizadas en la zona horaria configurada. */
@Component
public class AppClock {

    private final ZoneId zone;

    public AppClock(@Value("${app.time-zone}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public ZoneId zone() { return zone; }

    public ZonedDateTime now() { return ZonedDateTime.now(zone); }

    public LocalDate today() { return LocalDate.now(zone); }

    /** Primer instante del proximo mes (cuando se reinicia la cuota mensual). */
    public ZonedDateTime nextMonthlyReset() {
        return today().with(TemporalAdjusters.firstDayOfNextMonth()).atStartOfDay(zone);
    }

    /** Segundos que faltan para el inicio del proximo minuto (reset del rate limit). */
    public long secondsUntilNextMinute() {
        long millis = System.currentTimeMillis() % 60_000L;
        long secs = (60_000L - millis + 999) / 1000;
        return Math.max(1, secs);
    }

    /** Epoch (s) del inicio del proximo minuto. */
    public long nextMinuteEpochSeconds() {
        long now = System.currentTimeMillis() / 1000;
        return now - (now % 60) + 60;
    }
}
