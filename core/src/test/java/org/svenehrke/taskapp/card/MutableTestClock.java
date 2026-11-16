package org.svenehrke.taskapp.card;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** A Clock for unit tests of core that the test moves: in Europe/Zurich, at a settable instant. */
public class MutableTestClock extends Clock {

    public static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private Instant now;

    public MutableTestClock(LocalDate today) {
        setToday(today);
    }

    /** Noon of {@code today}. */
    public void setToday(LocalDate today) {
        set(today, LocalTime.NOON);
    }

    public void set(LocalDate day, LocalTime time) {
        now = day.atTime(time).atZone(ZONE).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return now;
    }
}
