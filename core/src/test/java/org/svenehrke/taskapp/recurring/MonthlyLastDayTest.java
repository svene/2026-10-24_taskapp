package org.svenehrke.taskapp.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MonthlyLastDayTest {

    /** 31, 30, 31, 31, then February: 28 in a common year. */
    @Test
    void follows_the_month_lengths() {
        var monthly = new MonthlyLastDay(LocalDate.of(2026, 10, 25));

        assertThat(monthly.preview(5)).containsExactly(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 30),
            LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28));
    }

    @Test
    void february_has_29_days_in_a_leap_year() {
        var monthly = new MonthlyLastDay(LocalDate.of(2028, 2, 1));

        assertThat(monthly.datesFrom(LocalDate.of(2028, 2, 1)).limit(2))
            .containsExactly(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 31));
        assertThat(monthly.produces(LocalDate.of(2028, 2, 28))).isFalse();
    }

    @Test
    void a_start_on_the_last_day_counts_and_nothing_before_it() {
        var monthly = new MonthlyLastDay(LocalDate.of(2026, 11, 30));

        assertThat(monthly.produces(LocalDate.of(2026, 11, 30))).isTrue();
        assertThat(monthly.produces(LocalDate.of(2026, 10, 31))).isFalse();
        assertThat(monthly.nextOnOrAfter(LocalDate.of(2026, 10, 1))).isEqualTo(LocalDate.of(2026, 11, 30));
    }
}
