package org.svenehrke.taskapp.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParsedScheduleTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 25);

    @Test
    void monthly_on_day_parses_its_day() {
        assertThat(ParsedSchedule.parse(new ParsedSchedule.Input("MONTHLY_DAY", null, null, " 15 ", "2026-10-25", null, null)))
            .isEqualTo(new ParsedSchedule.Valid(new MonthlyOnDay(15, START)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "29", "31", "-1", "x", ""})
    void monthly_on_day_needs_a_day_from_1_to_28(String day) {
        assertThat(ParsedSchedule.parse(new ParsedSchedule.Input("MONTHLY_DAY", null, null, day, "2026-10-25", null, null)))
            .isEqualTo(new ParsedSchedule.Invalid(Map.of("monthDay", "Day of the month needs a whole number from 1 to 28.")));
    }

    @Test
    void monthly_last_day_has_no_parameters_and_ignores_the_other_kinds_fields() {
        assertThat(ParsedSchedule.parse(new ParsedSchedule.Input("MONTHLY_LAST", List.of(), "0", "99", "2026-10-25", null, null)))
            .isEqualTo(new ParsedSchedule.Valid(new MonthlyLastDay(START)));
    }

    @Test
    void weekly_ignores_the_month_day() {
        assertThat(ParsedSchedule.parse(new ParsedSchedule.Input("WEEKLY", List.of("7"), "1", "99", "2026-10-25", null, null)))
            .isInstanceOf(ParsedSchedule.Valid.class);
    }

    @Test
    void an_unknown_kind_and_a_missing_start_date_are_reported() {
        assertThat(ParsedSchedule.parse(new ParsedSchedule.Input("YEARLY", null, null, null, " ", null, null)))
            .isEqualTo(new ParsedSchedule.Invalid(Map.of(
                "kind", "Choose how the task repeats.",
                "startsOn", "Start date is required.")));
    }

    @Test
    void after_done_reads_its_amount_and_unit() {
        assertThat(ParsedSchedule.parse(new ParsedSchedule.Input("AFTER_DONE", null, null, null, "2026-10-25", " 3 ", "WEEKS")))
            .isEqualTo(new ParsedSchedule.Valid(new AfterDone(3, AfterDone.Unit.WEEKS, LocalDate.of(2026, 10, 25))));
    }

    @Test
    void after_done_without_an_amount_or_a_unit_is_reported() {
        assertThat(ParsedSchedule.parse(new ParsedSchedule.Input("AFTER_DONE", null, null, null, "2026-10-25", "0", "MONTHS")))
            .isEqualTo(new ParsedSchedule.Invalid(Map.of(
                "afterAmount", "Every N days or weeks needs a whole number of at least 1.",
                "afterUnit", "Choose days or weeks.")));
    }
}
