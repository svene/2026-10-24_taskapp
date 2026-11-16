package org.svenehrke.taskapp.recurring;

import java.time.LocalDate;

/**
 * A row of the {@code recurring_task} table. It is never on the Board itself; its Schedule produces Occurrences.
 * {@code notes} may be null. {@code producedThrough}: the latest original due date produced so far (calendar kinds).
 * {@code stoppedOn}: null while the task produces.
 */
public record RecurringTask(long id, String title, String notes, Schedule schedule, LocalDate producedThrough,
    LocalDate stoppedOn) {

    public boolean stopped() {
        return stoppedOn != null;
    }

    /**
     * Whether an Occurrence of this task is Stale (spec §2.4), derived, never stored: open, due after {@code today},
     * and the task is Stopped or its current Schedule no longer produces the Occurrence's original due date. A
     * dragged Occurrence is judged by its original due date, wherever it sits now. After-done Occurrences become
     * Stale only through Stop.
     */
    public boolean stale(LocalDate originalDueOn, LocalDate dueOn, boolean done, LocalDate today) {
        if (done || !dueOn.isAfter(today)) return false;
        if (stopped()) return true;
        return schedule instanceof CalendarSchedule calendar && !calendar.produces(originalDueOn);
    }
}
