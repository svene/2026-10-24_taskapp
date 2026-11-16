package org.svenehrke.taskapp.recurring;

import java.time.LocalDate;

/**
 * A row of the {@code recurring_task} table. It is never on the Board itself; its Schedule produces Occurrences.
 * {@code notes} may be null. {@code producedThrough}: the latest original due date produced so far (calendar kinds).
 * {@code stoppedOn}: null while the task produces.
 */
public record RecurringTask(long id, String title, String notes, Schedule schedule, LocalDate producedThrough,
    LocalDate stoppedOn) {}
