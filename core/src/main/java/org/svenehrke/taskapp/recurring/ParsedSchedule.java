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
    String AFTER_AMOUNT = "afterAmount";
    String AFTER_UNIT = "afterUnit";

    String WEEKLY = "WEEKLY";
    String MONTHLY_DAY = "MONTHLY_DAY";
    String MONTHLY_LAST = "MONTHLY_LAST";
    String AFTER_DONE = "AFTER_DONE";

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
     * @param kind        {@link #WEEKLY}, {@link #MONTHLY_DAY}, {@link #MONTHLY_LAST} or {@link #AFTER_DONE}
     * @param weekdays    Weekly: the ticked weekdays as ISO numbers, 1 (Monday) to 7 (Sunday); may be null
     * @param everyWeeks  Weekly: the N of "every N weeks"
     * @param monthDay    Monthly on day: the N of "day N", 1–28
     * @param startsOn    ISO date
     * @param afterAmount After done: the N of "N days or weeks after done"
     * @param afterUnit   After done: {@code DAYS} or {@code WEEKS}
     */
    record Input(String kind, List<String> weekdays, String everyWeeks, String monthDay, String startsOn,
                 String afterAmount, String afterUnit) {}

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
            case AFTER_DONE -> {
                if (whole(input.afterAmount()) < 1)
                    errors.put(AFTER_AMOUNT, "Every N days or weeks needs a whole number of at least 1.");
                if (unit(input.afterUnit()) == null) errors.put(AFTER_UNIT, "Choose days or weeks.");
            }
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
            case AFTER_DONE -> new AfterDone(whole(input.afterAmount()), unit(input.afterUnit()), start);
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

    /** {@code DAYS} or {@code WEEKS}, or null for anything else. */
    private static AfterDone.Unit unit(String value) {
        if (value == null) return null;
        return switch (value.strip()) {
            case "DAYS" -> AfterDone.Unit.DAYS;
            case "WEEKS" -> AfterDone.Unit.WEEKS;
            default -> null;
        };
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
