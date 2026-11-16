package org.svenehrke.taskapp.recurring;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The New Task modal's preview of a Schedule: a calendar Schedule's first {@link #COUNT} dates ("Sat 31 Oct 2026"),
 * from its Start date on; an After-done Schedule's Start date only, with {@code message} saying how the rest follow.
 * Otherwise {@code message} says why there is no preview (the Schedule fields' messages), and is {@code null} for a
 * calendar Schedule.
 */
public record SchedulePreviewVM(List<String> dates, String message) {

    static final int COUNT = 5;

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

    public static SchedulePreviewVM of(ParsedSchedule.Input input) {
        return switch (ParsedSchedule.parse(input)) {
            case ParsedSchedule.Valid valid -> switch (valid.schedule()) {
                case Weekly weekly -> of(weekly);
                case MonthlyOnDay monthly -> of(monthly);
                case MonthlyLastDay monthly -> of(monthly);
                case AfterDone afterDone -> new SchedulePreviewVM(List.of(LABEL.format(afterDone.startsOn())),
                    "then " + afterDone.amount() + " " + unit(afterDone) + " after each time it is done");
            };
            case ParsedSchedule.Invalid invalid -> new SchedulePreviewVM(List.of(), String.join(" ", invalid.errors().values()));
        };
    }

    /** "day", "days", "week" or "weeks". */
    public static String unit(AfterDone afterDone) {
        var unit = afterDone.unit() == AfterDone.Unit.DAYS ? "day" : "week";
        return afterDone.amount() == 1 ? unit : unit + "s";
    }

    private static SchedulePreviewVM of(CalendarSchedule schedule) {
        return new SchedulePreviewVM(schedule.preview(COUNT).stream().map(LABEL::format).toList(), null);
    }
}
