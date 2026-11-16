package org.svenehrke.taskapp.card;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The One-off Task form as the Receiver got it: {@link Valid}, or {@link Invalid} with one message per field, so the
 * form can be re-rendered with them.
 */
public sealed interface ParsedOneOffTask {

    String TITLE = "title";
    String DUE_ON = "dueOn";

    /** Title trimmed and non-blank; blank notes are null. */
    record Valid(String title, String notes, LocalDate dueOn) implements ParsedOneOffTask {}

    /** {@code errors}: field name ({@link #TITLE}, {@link #DUE_ON}) → message. */
    record Invalid(Map<String, String> errors) implements ParsedOneOffTask {
        public Invalid {
            errors = Map.copyOf(errors);
        }
    }

    static ParsedOneOffTask parse(String title, String notes, String dueOn) {
        var errors = new LinkedHashMap<String, String>();
        var trimmedTitle = title == null ? "" : title.strip();
        if (trimmedTitle.isEmpty()) errors.put(TITLE, "Title must not be blank.");
        LocalDate due = null;
        if (dueOn == null || dueOn.isBlank()) {
            errors.put(DUE_ON, "Due date is required.");
        } else {
            try {
                due = LocalDate.parse(dueOn.strip());
            } catch (DateTimeParseException e) {
                errors.put(DUE_ON, "Due date must be a date (YYYY-MM-DD).");
            }
        }
        if (!errors.isEmpty()) return new Invalid(errors);
        var trimmedNotes = notes == null || notes.isBlank() ? null : notes.strip();
        return new Valid(trimmedTitle, trimmedNotes, due);
    }
}
