package org.svenehrke.taskapp.recurring;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Recurring Task form as the Receiver got it: {@link Valid}, or {@link Invalid} with one message per field, so the
 * form can be re-rendered with them. The Schedule fields are parsed by {@link ParsedSchedule}.
 */
public sealed interface ParsedRecurringTask {

    String TITLE = "title";

    /** Title trimmed and non-blank; blank notes are null. */
    record Valid(String title, String notes, Schedule schedule) implements ParsedRecurringTask {}

    /** {@code errors}: field name → message, in form order. */
    record Invalid(Map<String, String> errors) implements ParsedRecurringTask {
        public Invalid {
            errors = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(errors));
        }
    }

    static ParsedRecurringTask parse(String title, String notes, ParsedSchedule.Input schedule) {
        var errors = new LinkedHashMap<String, String>();
        var trimmedTitle = title == null ? "" : title.strip();
        if (trimmedTitle.isEmpty()) errors.put(TITLE, "Title must not be blank.");
        return switch (ParsedSchedule.parse(schedule)) {
            case ParsedSchedule.Invalid invalid -> {
                errors.putAll(invalid.errors());
                yield new Invalid(errors);
            }
            case ParsedSchedule.Valid valid -> errors.isEmpty()
                ? new Valid(trimmedTitle, notes == null || notes.isBlank() ? null : notes.strip(), valid.schedule())
                : new Invalid(errors);
        };
    }
}
