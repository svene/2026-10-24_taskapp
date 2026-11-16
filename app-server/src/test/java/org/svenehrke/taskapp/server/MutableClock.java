package org.svenehrke.taskapp.server;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Replaces the system Clock in every {@code @QuarkusTest}: today is {@link #DEFAULT_TODAY} (a Sunday) until a
 * test sets another day. Tests that set it call {@link #reset()} afterwards.
 */
@Alternative
@Priority(1)
@Singleton
public class MutableClock extends Clock {

    public static final LocalDate DEFAULT_TODAY = LocalDate.of(2026, 10, 25);
    private static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private volatile Instant instant;

    public MutableClock() {
        reset();
    }

    public void setToday(LocalDate today) {
        instant = today.atTime(LocalTime.NOON).atZone(ZONE).toInstant();
    }

    public void reset() {
        setToday(DEFAULT_TODAY);
    }

    @Override
    public ZoneId getZone() {
        return ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
