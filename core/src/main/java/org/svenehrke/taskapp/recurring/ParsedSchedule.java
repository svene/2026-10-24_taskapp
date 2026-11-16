package org.svenehrke.taskapp.recurring;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Schedule fields of the Recurring Task form as the Receiver got them: {@link Valid}, or {@link Invalid} with one
 * message per field. Only the chosen kind's fields are read. Shared by creating a Recurring Task and by the modal's
 * live preview.
 */
public sealed interface ParsedSchedule {

    String KIND = "kind";
    String WEEKDAYS = "weekdays";
    String EVERY_WEEKS = "everyWeeks";
    String MONTH_DAY = "monthDay";
    String STARTS_ON = "startsOn";

    String WEEKLY = "WEEKLY";
    String MONTHLY_DAY = "MONTHLY_DAY";
    String MONTHLY_LAST = "MONTHLY_LAST";

    record Valid(Schedule schedule) implements ParsedSchedule {}

    /** {@code errors}: field name → message, in form order. */
    record Invalid(Map<String, String> errors) implements ParsedSchedule {
        public Invalid {
            errors = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(errors));
        }
    }

    /**
     * The Schedule fields as typed.
     *
     * @param kind       {@link #WEEKLY}, {@link #MONTHLY_DAY} or {@link #MONTHLY_LAST}
     * @param weekdays   Weekly: the ticked weekdays as ISO numbers, 1 (Monday) to 7 (Sunday); may be null
     * @param everyWeeks Weekly: the N of "every N weeks"
     * @param monthDay   Monthly on day: the N of "day N", 1–28
     * @param startsOn   ISO date
     */
    record Input(String kind, List<String> weekdays, String everyWeeks, String monthDay, String startsOn) {}

    static ParsedSchedule parse(Input input) {
        var errors = errors(input);
        return errors.isEmpty() ? new Valid(schedule(input)) : new Invalid(errors);
    }

    /** A message per invalid field. */
    private static Map<String, String> errors(Input input) {
        var errors = new LinkedHashMap<String, String>();
        var kind = input.kind() == null ? "" : input.kind();
        switch (kind) {
            case WEEKLY -> {
                if (weekdays(input.weekdays()).isEmpty()) errors.put(WEEKDAYS, "Choose at least one weekday.");
                if (whole(input.everyWeeks()) < 1)
                    errors.put(EVERY_WEEKS, "Every N weeks needs a whole number of at least 1.");
            }
            case MONTHLY_DAY -> {
                int day = whole(input.monthDay());
                if (day < 1 || day > 28) errors.put(MONTH_DAY, "Day of the month needs a whole number from 1 to 28.");
            }
            case MONTHLY_LAST -> {}
            default -> errors.put(KIND, "Choose how the task repeats.");
        }
        if (input.startsOn() == null || input.startsOn().isBlank()) {
            errors.put(STARTS_ON, "Start date is required.");
        } else if (startsOn(input) == null) {
            errors.put(STARTS_ON, "Start date must be a date (YYYY-MM-DD).");
        }
        return errors;
    }

    /** Only when {@link #errors(Input)} found nothing. */
    private static Schedule schedule(Input input) {
        var start = startsOn(input);
        return switch (input.kind()) {
            case WEEKLY -> new Weekly(weekdays(input.weekdays()), whole(input.everyWeeks()), start);
            case MONTHLY_DAY -> new MonthlyOnDay(whole(input.monthDay()), start);
            case MONTHLY_LAST -> new MonthlyLastDay(start);
            default -> throw new IllegalStateException("unreachable: " + input.kind());
        };
    }

    private static LocalDate startsOn(Input input) {
        try {
            return LocalDate.parse(input.startsOn().strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** The valid ISO weekday numbers among {@code values}; anything else is ignored. */
    private static EnumSet<DayOfWeek> weekdays(List<String> values) {
        var days = EnumSet.noneOf(DayOfWeek.class);
        if (values == null) return days;
        for (var value : values) {
            int number = whole(value);
            if (number >= 1 && number <= 7) days.add(DayOfWeek.of(number));
        }
        return days;
    }

    /** The whole number in {@code value}, or 0 when there is none (or it is negative). */
    private static int whole(String value) {
        try {
            return value == null ? 0 : Math.max(0, Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
