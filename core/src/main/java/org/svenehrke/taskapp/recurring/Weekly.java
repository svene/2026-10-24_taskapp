package org.svenehrke.taskapp.recurring;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The chosen {@code weekdays}, every {@code everyWeeks}th ISO week (Monday to Sunday), counted from the week that
 * contains {@code startsOn}. Nothing before {@code startsOn}, even in that first week.
 */
public record Weekly(Set<DayOfWeek> weekdays, int everyWeeks, LocalDate startsOn) implements Schedule, CalendarSchedule {

    public Weekly {
        if (weekdays.isEmpty()) throw new IllegalArgumentException("A Weekly Schedule needs at least one weekday.");
        if (everyWeeks < 1) throw new IllegalArgumentException("every N weeks needs N >= 1, was " + everyWeeks);
        Objects.requireNonNull(startsOn);
        weekdays = Collections.unmodifiableSet(EnumSet.copyOf(weekdays));
    }

    @Override
    public boolean produces(LocalDate day) {
        return !day.isBefore(startsOn) && weekdays.contains(day.getDayOfWeek())
            && ChronoUnit.WEEKS.between(monday(startsOn), monday(day)) % everyWeeks == 0;
    }

    @Override
    public LocalDate nextOnOrAfter(LocalDate day) {
        var date = day.isBefore(startsOn) ? startsOn : day;
        // within 2 cycles there is always a produced week with every weekday from Monday on
        for (int i = 0; i < 14 * everyWeeks; i++, date = date.plusDays(1)) {
            if (produces(date)) return date;
        }
        throw new IllegalStateException("unreachable: " + this + " produces nothing after " + day);
    }

    private static LocalDate monday(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
