package org.svenehrke.taskapp.recurring;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/** The last day of every month (28, 29, 30 or 31), from {@code startsOn} on. */
public record MonthlyLastDay(LocalDate startsOn) implements Schedule, CalendarSchedule {

    public MonthlyLastDay {
        Objects.requireNonNull(startsOn);
    }

    @Override
    public boolean produces(LocalDate date) {
        return !date.isBefore(startsOn) && date.getDayOfMonth() == date.lengthOfMonth();
    }

    @Override
    public LocalDate nextOnOrAfter(LocalDate date) {
        return (date.isBefore(startsOn) ? startsOn : date).with(TemporalAdjusters.lastDayOfMonth());
    }
}
