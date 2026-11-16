package org.svenehrke.taskapp.card;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.svenehrke.taskapp.shared.RejectedException;

/** Where a Move sends a Card: a {@link Day}, or {@link Future}, which the core turns into the day after the span. */
public sealed interface MoveTarget {

    record Day(LocalDate date) implements MoveTarget {}

    record Future() implements MoveTarget {}

    /**
     * {@code future}, or an ISO date.
     *
     * @throws RejectedException for anything else
     */
    static MoveTarget parse(String target) {
        var value = target == null ? "" : target.strip();
        if (value.equals("future")) return new Future();
        try {
            return new Day(LocalDate.parse(value));
        } catch (DateTimeParseException e) {
            throw new RejectedException("A task can only be moved to a day or to Future.");
        }
    }
}
