package org.svenehrke.taskapp.recurring;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Recurring Task form as the Receiver got it: {@link Valid}, or {@link Invalid} with one message per field, so the
 * form can be re-rendered with them. Only the {@code WEEKLY} kind so far.
 */
public sealed interface ParsedRecurringTask {

    String TITLE = "title";
    String KIND = "kind";
    String WEEKDAYS = "weekdays";
    String EVERY_WEEKS = "everyWeeks";
    String STARTS_ON = "startsOn";

    String WEEKLY = "WEEKLY";

    /** Title trimmed and non-blank; blank notes are null. */
    record Valid(String title, String notes, Schedule schedule) implements ParsedRecurringTask {}

    /** {@code errors}: field name → message. */
    record Invalid(Map<String, String> errors) implements ParsedRecurringTask {
        public Invalid {
            errors = Map.copyOf(errors);
        }
    }

    /**
     * @param weekdays   the ticked weekdays as ISO numbers, 1 (Monday) to 7 (Sunday); may be null
     * @param everyWeeks the N of "every N weeks"
     * @param startsOn   ISO date
     */
    static ParsedRecurringTask parse(String title, String notes, String kind, List<String> weekdays, String everyWeeks,
        String startsOn) {
        var errors = new LinkedHashMap<String, String>();
        var trimmedTitle = title == null ? "" : title.strip();
        if (trimmedTitle.isEmpty()) errors.put(TITLE, "Title must not be blank.");
        if (!WEEKLY.equals(kind)) errors.put(KIND, "Choose how the task repeats.");
        var days = weekdays(weekdays);
        if (days.isEmpty()) errors.put(WEEKDAYS, "Choose at least one weekday.");
        int every = positive(everyWeeks);
        if (every < 1) errors.put(EVERY_WEEKS, "Every N weeks needs a whole number of at least 1.");
        LocalDate start = null;
        if (startsOn == null || startsOn.isBlank()) {
            errors.put(STARTS_ON, "Start date is required.");
        } else {
            try {
                start = LocalDate.parse(startsOn.strip());
            } catch (DateTimeParseException e) {
                errors.put(STARTS_ON, "Start date must be a date (YYYY-MM-DD).");
            }
        }
        if (!errors.isEmpty()) return new Invalid(errors);
        var trimmedNotes = notes == null || notes.isBlank() ? null : notes.strip();
        return new Valid(trimmedTitle, trimmedNotes, new Weekly(days, every, start));
    }

    /** The valid ISO weekday numbers among {@code values}; anything else is ignored. */
    private static EnumSet<DayOfWeek> weekdays(List<String> values) {
        var days = EnumSet.noneOf(DayOfWeek.class);
        if (values == null) return days;
        for (var value : values) {
            int number = positive(value);
            if (number >= 1 && number <= 7) days.add(DayOfWeek.of(number));
        }
        return days;
    }

    /** The whole number in {@code value}, or 0 when there is none. */
    private static int positive(String value) {
        try {
            return value == null ? 0 : Math.max(0, Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
