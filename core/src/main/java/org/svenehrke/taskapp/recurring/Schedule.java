package org.svenehrke.taskapp.recurring;

import java.time.LocalDate;

/**
 * When a Recurring Task produces Occurrences (spec §2.2). Only the current Schedule is stored; an edit overwrites it.
 * The After-done kind joins the permitted list in its own slice.
 */
public sealed interface Schedule permits Weekly, MonthlyOnDay, MonthlyLastDay {

    /** Nothing is produced before this day. */
    LocalDate startsOn();
}
