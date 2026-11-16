package org.svenehrke.taskapp.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.svenehrke.taskapp.recurring.AfterDone.Unit;

class AfterDoneTest {

    private static final LocalDate SUN_25_OCT = LocalDate.of(2026, 10, 25);

    @Test
    void the_next_occurrence_is_n_weeks_after_the_done_date() {
        var schedule = new AfterDone(3, Unit.WEEKS, SUN_25_OCT);

        assertThat(schedule.next(SUN_25_OCT, SUN_25_OCT, LocalDate.of(2026, 10, 27))).isEqualTo(LocalDate.of(2026, 11, 17));
    }

    @Test
    void the_next_occurrence_is_n_days_after_the_done_date() {
        var schedule = new AfterDone(10, Unit.DAYS, SUN_25_OCT);

        assertThat(schedule.next(SUN_25_OCT, SUN_25_OCT, LocalDate.of(2026, 10, 24))).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    /** Ticked off long before it was due: done date + N would not move past it, so the next day follows. */
    @Test
    void the_next_occurrence_is_at_least_the_day_after_the_ticked_ones_original_due_date() {
        var schedule = new AfterDone(2, Unit.DAYS, LocalDate.of(2026, 11, 20));

        assertThat(schedule.next(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20), SUN_25_OCT)).isEqualTo(LocalDate.of(2026, 11, 21));
    }

    /** Moved ahead from 25 to 26 Oct, then ticked off early on 25 Oct: not next to the Done Card on 26 Oct. */
    @Test
    void the_next_occurrence_is_at_least_the_day_after_the_day_the_ticked_one_was_moved_to() {
        var schedule = new AfterDone(1, Unit.DAYS, SUN_25_OCT);

        assertThat(schedule.next(SUN_25_OCT, LocalDate.of(2026, 10, 26), SUN_25_OCT)).isEqualTo(LocalDate.of(2026, 10, 27));
    }

    /** Moved back from 20 to 18 Nov: the successor still lies after the original due date, so it stays the latest. */
    @Test
    void the_next_occurrence_is_at_least_the_day_after_the_original_due_date_when_moved_back() {
        var schedule = new AfterDone(1, Unit.DAYS, LocalDate.of(2026, 11, 20));

        assertThat(schedule.next(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 18), SUN_25_OCT))
            .isEqualTo(LocalDate.of(2026, 11, 21));
    }

    @Test
    void needs_at_least_one_day_or_week() {
        assertThatThrownBy(() -> new AfterDone(0, Unit.DAYS, SUN_25_OCT)).isInstanceOf(IllegalArgumentException.class);
    }
}
