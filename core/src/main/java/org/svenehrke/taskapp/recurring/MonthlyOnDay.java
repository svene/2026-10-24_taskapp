package org.svenehrke.taskapp.recurring;

import java.time.LocalDate;
import java.util.Objects;

/** Day {@code day} (1–28, so every month has it) of every month, from {@code startsOn} on. */
public record MonthlyOnDay(int day, LocalDate startsOn) implements Schedule, CalendarSchedule {

    public MonthlyOnDay {
        if (day < 1 || day > 28) throw new IllegalArgumentException("Monthly on day N needs 1 <= N <= 28, was " + day);
        Objects.requireNonNull(startsOn);
    }

    @Override
    public boolean produces(LocalDate date) {
        return !date.isBefore(startsOn) && date.getDayOfMonth() == day;
    }

    @Override
    public LocalDate nextOnOrAfter(LocalDate date) {
        var from = date.isBefore(startsOn) ? startsOn : date;
        var candidate = from.withDayOfMonth(day);
        return candidate.isBefore(from) ? candidate.plusMonths(1) : candidate;
    }
}
