package org.svenehrke.taskapp.recurring;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.svenehrke.taskapp.recurring.InMemoryRecurringTaskRepository.Occurrence;

class OccurrenceProducerTest {

    private static final LocalDate SUN_25_OCT = LocalDate.of(2026, 10, 25);

    private final InMemoryRecurringTaskRepository tasks = new InMemoryRecurringTaskRepository();
    private final RecurringTaskHandler handler = new RecurringTaskHandler(tasks);
    private final OccurrenceProducer producer = new OccurrenceProducer(tasks);

    /** The example of the slice: Mon and Thu every week, from today. */
    @Test
    void produces_the_span_plus_the_first_date_beyond_it() {
        handler.create("Water plants", null, new Weekly(Set.of(MONDAY, THURSDAY), 1, SUN_25_OCT));

        producer.catchUp(SUN_25_OCT);

        assertThat(tasks.occurrences()).extracting(Occurrence::originalDueOn).containsExactly(
            date(10, 26), date(10, 29), date(11, 2), date(11, 5), date(11, 9));
        assertThat(tasks.find(1).orElseThrow().producedThrough()).isEqualTo(date(11, 9));
    }

    /** The example of the Monthly slice: 31 Oct in the span, 30 Nov the first date beyond it (Future). */
    @Test
    void monthly_on_the_last_day_produces_the_span_plus_the_first_date_beyond_it() {
        handler.create("Pay rent", null, new MonthlyLastDay(SUN_25_OCT));

        producer.catchUp(SUN_25_OCT);

        assertThat(tasks.occurrences()).extracting(Occurrence::originalDueOn).containsExactly(date(10, 31), date(11, 30));
        assertThat(tasks.find(1).orElseThrow().producedThrough()).isEqualTo(date(11, 30));
    }

    @Test
    void a_second_catch_up_the_same_day_creates_nothing() {
        handler.create("Water plants", null, new Weekly(Set.of(MONDAY, THURSDAY), 1, SUN_25_OCT));
        producer.catchUp(SUN_25_OCT);

        producer.catchUp(SUN_25_OCT);

        assertThat(tasks.occurrences()).hasSize(5);
    }

    @Test
    void a_past_start_date_backfills_overdue_occurrences() {
        handler.create("Water plants", null, new Weekly(Set.of(MONDAY, THURSDAY), 1, date(10, 15)));

        producer.catchUp(SUN_25_OCT);

        assertThat(tasks.occurrences()).extracting(Occurrence::originalDueOn).startsWith(
            date(10, 15), date(10, 19), date(10, 22), date(10, 26));
    }

    /** Laptop off for 10 days: each missed date is produced once, and the span moves on. */
    @Test
    void days_without_a_board_read_are_caught_up_once() {
        handler.create("Water plants", null, new Weekly(Set.of(MONDAY, THURSDAY), 1, SUN_25_OCT));
        producer.catchUp(SUN_25_OCT);

        producer.catchUp(SUN_25_OCT.plusDays(10));

        assertThat(tasks.occurrences()).extracting(Occurrence::originalDueOn).containsExactly(
            date(10, 26), date(10, 29), date(11, 2), date(11, 5), date(11, 9),
            date(11, 12), date(11, 16), date(11, 19));
        assertThat(tasks.find(1).orElseThrow().producedThrough()).isEqualTo(date(11, 19));
    }

    @Test
    void a_stopped_task_produces_nothing() {
        handler.create("Water plants", null, new Weekly(Set.of(MONDAY, THURSDAY), 1, SUN_25_OCT));
        tasks.stop(1, SUN_25_OCT);

        producer.catchUp(SUN_25_OCT);

        assertThat(tasks.occurrences()).isEmpty();
    }

    private static LocalDate date(int month, int day) {
        return LocalDate.of(2026, month, day);
    }
}
