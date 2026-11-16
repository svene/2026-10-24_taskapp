package org.svenehrke.taskapp.recurring;

import java.time.LocalDate;

/**
 * When a Recurring Task produces Occurrences (spec §2.2). Only the current Schedule is stored; an edit overwrites it.
 * Monthly and After-done kinds join the permitted list in their own slices.
 */
public sealed interface Schedule permits Weekly {

    /** Nothing is produced before this day. */
    LocalDate startsOn();
}
