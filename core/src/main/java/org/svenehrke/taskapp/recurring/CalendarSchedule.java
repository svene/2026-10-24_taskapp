package org.svenehrke.taskapp.recurring;

import java.time.LocalDate;
import java.util.stream.Stream;

/** A Schedule of a calendar kind: its dates follow from the calendar alone, so catch-up can produce them ahead. */
public interface CalendarSchedule {

    /** Whether this Schedule produces an Occurrence on {@code day}. */
    boolean produces(LocalDate day);

    /** The first day on or after {@code day} that this Schedule produces. */
    LocalDate nextOnOrAfter(LocalDate day);

    /** Every day this Schedule produces, from {@code day} on, in order (an endless stream). */
    default Stream<LocalDate> datesFrom(LocalDate day) {
        return Stream.iterate(nextOnOrAfter(day), date -> nextOnOrAfter(date.plusDays(1)));
    }
}
