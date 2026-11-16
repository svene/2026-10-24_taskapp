package org.svenehrke.taskapp.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ParsedOneOffTaskTest {

    @Test
    void valid_input_is_trimmed_and_blank_notes_become_null() {
        assertThat(ParsedOneOffTask.parse("  Water the plants ", "  ", "2026-10-26"))
            .isEqualTo(new ParsedOneOffTask.Valid("Water the plants", null, LocalDate.of(2026, 10, 26)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void a_blank_title_is_invalid(String title) {
        assertThat(ParsedOneOffTask.parse(title, null, "2026-10-26"))
            .isEqualTo(new ParsedOneOffTask.Invalid(Map.of("title", "Title must not be blank.")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void the_due_date_is_mandatory(String dueOn) {
        assertThat(ParsedOneOffTask.parse("Water the plants", null, dueOn))
            .isEqualTo(new ParsedOneOffTask.Invalid(Map.of("dueOn", "Due date is required.")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"26.10.2026", "2026-02-30", "tomorrow"})
    void an_unparseable_due_date_is_invalid(String dueOn) {
        assertThat(ParsedOneOffTask.parse("Water the plants", null, dueOn))
            .isEqualTo(new ParsedOneOffTask.Invalid(Map.of("dueOn", "Due date must be a date (YYYY-MM-DD).")));
    }

    @Test
    void reports_every_invalid_field() {
        assertThat(ParsedOneOffTask.parse(" ", null, "")).isInstanceOfSatisfying(ParsedOneOffTask.Invalid.class,
            invalid -> assertThat(invalid.errors()).containsOnlyKeys("title", "dueOn"));
    }
}
