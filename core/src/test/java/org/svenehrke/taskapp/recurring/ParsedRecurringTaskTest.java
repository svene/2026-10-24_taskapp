package org.svenehrke.taskapp.recurring;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ParsedRecurringTaskTest {

    @Test
    void a_weekly_form_parses_to_a_trimmed_task_with_its_schedule() {
        var parsed = ParsedRecurringTask.parse("  Water plants ", "  ",
            new ParsedSchedule.Input("WEEKLY", List.of("4", "1"), "2", null, "2026-10-25", null, null));

        assertThat(parsed).isEqualTo(new ParsedRecurringTask.Valid("Water plants", null,
            new Weekly(Set.of(MONDAY, THURSDAY), 2, LocalDate.of(2026, 10, 25))));
    }

    @Test
    void every_field_reports_its_own_error() {
        var parsed = ParsedRecurringTask.parse(" ", null, new ParsedSchedule.Input("WEEKLY", null, "0", null, "25.10.2026", null, null));

        assertThat(parsed).isEqualTo(new ParsedRecurringTask.Invalid(Map.of(
            "title", "Title must not be blank.",
            "weekdays", "Choose at least one weekday.",
            "everyWeeks", "Every N weeks needs a whole number of at least 1.",
            "startsOn", "Start date must be a date (YYYY-MM-DD).")));
    }

    @Test
    void a_valid_schedule_with_a_blank_title_is_invalid() {
        var parsed = ParsedRecurringTask.parse("", null, new ParsedSchedule.Input("MONTHLY_LAST", null, null, null, "2026-10-25", null, null));

        assertThat(parsed).isEqualTo(new ParsedRecurringTask.Invalid(Map.of("title", "Title must not be blank.")));
    }
}
