package org.svenehrke.taskapp.recurring;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The first Occurrence falls on {@code startsOn}; each next one {@code amount} days or weeks after the day the latest
 * Occurrence was ticked off (spec §2.2). Not a {@link CalendarSchedule}: the catch-up never produces it.
 */
public record AfterDone(int amount, Unit unit, LocalDate startsOn) implements Schedule {

    public enum Unit { DAYS, WEEKS }

    public AfterDone {
        if (amount < 1) throw new IllegalArgumentException("every N days or weeks needs N >= 1, was " + amount);
        Objects.requireNonNull(unit);
        Objects.requireNonNull(startsOn);
    }

    /**
     * The original due date of the Occurrence that follows one due on {@code originalDueOn}, moved to {@code dueOn} and
     * ticked off on {@code doneOn}: {@code doneOn} plus N, but at least the day after the later of the two due dates, so
     * ticking off ahead of time still moves the chain forward and never puts the successor next to the Done Card.
     */
    public LocalDate next(LocalDate originalDueOn, LocalDate dueOn, LocalDate doneOn) {
        var next = unit == Unit.DAYS ? doneOn.plusDays(amount) : doneOn.plusWeeks(amount);
        var latestDue = dueOn.isAfter(originalDueOn) ? dueOn : originalDueOn;
        return next.isAfter(latestDue) ? next : latestDue.plusDays(1);
    }
}
