package org.svenehrke.taskapp.recurring;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeeklyTest {

    private static final LocalDate SUN_25_OCT = LocalDate.of(2026, 10, 25);

    @Test
    void every_week_produces_the_chosen_weekdays_from_the_start_date_on() {
        var weekly = new Weekly(Set.of(MONDAY, THURSDAY), 1, SUN_25_OCT);

        assertThat(weekly.datesFrom(LocalDate.of(2026, 10, 1)).limit(4)).containsExactly(
            LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 5));
    }

    @Test
    void nothing_before_the_start_date_even_in_its_week() {
        var weekly = new Weekly(Set.of(MONDAY, THURSDAY), 1, LocalDate.of(2026, 10, 27));

        assertThat(weekly.produces(LocalDate.of(2026, 10, 26))).isFalse();
        assertThat(weekly.nextOnOrAfter(LocalDate.of(2026, 10, 26))).isEqualTo(LocalDate.of(2026, 10, 29));
    }

    /** The phase counts ISO weeks from the week containing the Start date, not 14 days from the Start date. */
    @Test
    void every_second_week_counts_from_the_iso_week_of_the_start_date() {
        var weekly = new Weekly(Set.of(MONDAY, THURSDAY), 2, SUN_25_OCT);

        // 25 Oct is the Sunday of week 19-25 Oct, so the produced weeks are 19 Oct, 2 Nov, 16 Nov...
        assertThat(weekly.datesFrom(SUN_25_OCT).limit(4)).containsExactly(
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 19));
    }

    @Test
    void every_third_sunday() {
        var weekly = new Weekly(Set.of(SUNDAY), 3, SUN_25_OCT);

        assertThat(weekly.datesFrom(SUN_25_OCT).limit(3)).containsExactly(
            SUN_25_OCT, LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 6));
    }

    @Test
    void needs_a_weekday_and_n_of_at_least_one() {
        assertThatThrownBy(() -> new Weekly(Set.of(), 1, SUN_25_OCT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Weekly(Set.of(MONDAY), 0, SUN_25_OCT)).isInstanceOf(IllegalArgumentException.class);
    }
}
