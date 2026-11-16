package org.svenehrke.taskapp.recurring;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: the {@code recurring_task} table, and the Occurrence inserts into {@code card} ({@code recurring}
 * never depends on {@code card}, spec §4.1).
 */
public interface RecurringTaskRepositorySPI {

    /**
     * Inserts a Recurring Task and returns its id; {@code notes} may be null, and so may {@code producedThrough}
     * (After-done Schedules have none).
     */
    long insert(String title, String notes, Schedule schedule, LocalDate producedThrough);

    Optional<RecurringTask> find(long id);

    /** Every Recurring Task with a calendar Schedule that is not Stopped, ordered by id. */
    List<RecurringTask> producingOnCalendar();

    /**
     * An Occurrence due on its original due date {@code dueOn}, at the bottom of that day. Does nothing when the task
     * already has an Occurrence with that original due date (idempotent, spec §2.3).
     */
    void insertOccurrence(long recurringTaskId, LocalDate dueOn);

    /** The highest original due date among the task's Occurrences, Done or not; empty when it has none. */
    Optional<LocalDate> latestOriginalDueOn(long recurringTaskId);

    void setProducedThrough(long id, LocalDate producedThrough);
}
