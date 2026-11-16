package org.svenehrke.taskapp.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MonthlyOnDayTest {

    @Test
    void a_mid_month_start_after_the_day_begins_next_month() {
        var monthly = new MonthlyOnDay(15, LocalDate.of(2026, 10, 25));

        assertThat(monthly.datesFrom(LocalDate.of(2026, 10, 1)).limit(3)).containsExactly(
            LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 15));
    }

    @Test
    void a_mid_month_start_before_the_day_begins_this_month() {
        var monthly = new MonthlyOnDay(28, LocalDate.of(2026, 10, 25));

        assertThat(monthly.datesFrom(LocalDate.of(2026, 10, 25)).limit(2))
            .containsExactly(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 28));
    }

    @Test
    void the_start_date_itself_counts_when_it_is_the_day() {
        var monthly = new MonthlyOnDay(25, LocalDate.of(2026, 10, 25));

        assertThat(monthly.produces(LocalDate.of(2026, 10, 25))).isTrue();
        assertThat(monthly.produces(LocalDate.of(2026, 9, 25))).isFalse();
    }

    /** Day 28 exists in every month, so February in a common and in a leap year is never skipped. */
    @Test
    void day_28_falls_in_every_february() {
        var monthly = new MonthlyOnDay(28, LocalDate.of(2027, 1, 1));

        assertThat(monthly.datesFrom(LocalDate.of(2027, 1, 1)).limit(3)).containsExactly(
            LocalDate.of(2027, 1, 28), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 28));
        assertThat(monthly.nextOnOrAfter(LocalDate.of(2028, 2, 1))).isEqualTo(LocalDate.of(2028, 2, 28));
    }

    @Test
    void the_preview_is_the_first_dates_from_the_start_date() {
        var monthly = new MonthlyOnDay(1, LocalDate.of(2026, 11, 2));

        assertThat(monthly.preview(5)).containsExactly(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1),
            LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1), LocalDate.of(2027, 4, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 29, 31})
    void the_day_is_1_to_28(int day) {
        assertThatThrownBy(() -> new MonthlyOnDay(day, LocalDate.of(2026, 10, 25)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
