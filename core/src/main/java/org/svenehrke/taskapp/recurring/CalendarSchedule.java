package org.svenehrke.taskapp.recurring;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/** A Schedule of a calendar kind: its dates follow from the calendar alone, so catch-up can produce them ahead. */
public interface CalendarSchedule {

    /** Nothing is produced before this day. */
    LocalDate startsOn();

    /** Whether this Schedule produces an Occurrence on {@code day}. */
    boolean produces(LocalDate day);

    /** The first day on or after {@code day} that this Schedule produces. */
    LocalDate nextOnOrAfter(LocalDate day);

    /** Every day this Schedule produces, from {@code day} on, in order (an endless stream). */
    default Stream<LocalDate> datesFrom(LocalDate day) {
        return Stream.iterate(nextOnOrAfter(day), date -> nextOnOrAfter(date.plusDays(1)));
    }

    /**
     * The modal's live preview: the first {@code count} days this Schedule produces from its Start date on, which are
     * the first Occurrences it will create (a past Start date backfills, spec §2.3).
     */
    default List<LocalDate> preview(int count) {
        return datesFrom(startsOn()).limit(count).toList();
    }
}
