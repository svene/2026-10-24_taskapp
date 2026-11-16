package org.svenehrke.taskapp.recurring;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The New Task modal's preview of a calendar Schedule: its first {@link #COUNT} dates ("Sat 31 Oct 2026"), from its
 * Start date on. {@code message}: why there is no preview (the Schedule fields' messages), {@code null} otherwise.
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
            };
            case ParsedSchedule.Invalid invalid -> new SchedulePreviewVM(List.of(), String.join(" ", invalid.errors().values()));
        };
    }

    private static SchedulePreviewVM of(CalendarSchedule schedule) {
        return new SchedulePreviewVM(schedule.preview(COUNT).stream().map(LABEL::format).toList(), null);
    }
}
