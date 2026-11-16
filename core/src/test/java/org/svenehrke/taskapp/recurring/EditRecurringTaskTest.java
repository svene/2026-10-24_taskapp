package org.svenehrke.taskapp.recurring;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.svenehrke.taskapp.card.MutableTestClock;
import org.svenehrke.taskapp.recurring.InMemoryRecurringTaskRepository.Occurrence;
import org.svenehrke.taskapp.shared.RejectedException;

/** Editing and Stopping a Recurring Task (spec §2.4), with the Clock on Sun 25 Oct 2026. */
class EditRecurringTaskTest {

    private static final LocalDate SUN_25_OCT = LocalDate.of(2026, 10, 25);

    private final InMemoryRecurringTaskRepository tasks = new InMemoryRecurringTaskRepository();
    private final MutableTestClock clock = new MutableTestClock(SUN_25_OCT);
    private final OccurrenceProducer producer = new OccurrenceProducer(tasks);
    private final RecurringTaskHandler handler = new RecurringTaskHandler(clock, tasks, producer);

    /** The example of the slice: Mon and Thu becomes Mon only; the Thursdays stay, Stale; the Mondays are adopted. */
    @Test
    void an_edited_calendar_schedule_adopts_what_it_still_produces_and_leaves_the_rest_stale() {
        handler.create("Water plants", null, new Weekly(Set.of(MONDAY, THURSDAY), 1, SUN_25_OCT));
        producer.catchUp(SUN_25_OCT);

        handler.edit(1, "Water the plants", "rain water", new Weekly(Set.of(MONDAY), 1, SUN_25_OCT));

        var task = tasks.find(1).orElseThrow();
        assertThat(task.title()).isEqualTo("Water the plants");
        assertThat(task.notes()).isEqualTo("rain water");
        assertThat(tasks.occurrences()).extracting(Occurrence::originalDueOn).containsExactly(
            date(10, 26), date(10, 29), date(11, 2), date(11, 5), date(11, 9));
        assertThat(task.stale(date(10, 29), date(10, 29), false, SUN_25_OCT)).isTrue();
        assertThat(task.stale(date(11, 5), date(11, 5), false, SUN_25_OCT)).isTrue();
        assertThat(task.stale(date(11, 2), date(11, 2), false, SUN_25_OCT)).isFalse();
    }

    @Test
    void an_edit_never_backfills_before_today() {
        handler.create("Water plants", null, new Weekly(Set.of(MONDAY), 1, SUN_25_OCT));
        producer.catchUp(SUN_25_OCT);
        clock.setToday(date(11, 4));

        handler.edit(1, "Water plants", null, new Weekly(Set.of(MONDAY, THURSDAY), 1, SUN_25_OCT));

        assertThat(tasks.occurrences()).extracting(Occurrence::originalDueOn).doesNotContain(date(10, 29));
        assertThat(tasks.occurrences()).extracting(Occurrence::originalDueOn).contains(date(11, 5), date(11, 12));
    }

    @Test
    void a_dragged_occurrence_is_judged_by_its_original_due_date() {
        var task = new RecurringTask(1, "Water plants", null, new Weekly(Set.of(MONDAY), 1, SUN_25_OCT), date(11, 9), null);

        assertThat(task.stale(date(10, 29), date(11, 2), false, SUN_25_OCT)).isTrue();
        assertThat(task.stale(date(11, 2), date(10, 29), false, SUN_25_OCT)).isFalse();
    }

    @Test
    void an_occurrence_due_today_or_done_is_never_stale() {
        var task = new RecurringTask(1, "Water plants", null, new Weekly(Set.of(MONDAY), 1, SUN_25_OCT), date(11, 9), SUN_25_OCT);

        assertThat(task.stale(date(10, 29), SUN_25_OCT, false, SUN_25_OCT)).isFalse();
        assertThat(task.stale(date(10, 29), date(10, 29), true, SUN_25_OCT)).isFalse();
        assertThat(task.stale(date(10, 29), date(10, 29), false, SUN_25_OCT)).isTrue();
    }

    @Test
    void after_done_occurrences_become_stale_only_through_stop() {
        handler.create("Descale the kettle", null, new AfterDone(3, AfterDone.Unit.WEEKS, date(10, 30)));
        assertThat(tasks.find(1).orElseThrow().stale(date(10, 30), date(10, 30), false, SUN_25_OCT)).isFalse();

        handler.stop(1);

        assertThat(tasks.find(1).orElseThrow().stoppedOn()).isEqualTo(SUN_25_OCT);
        assertThat(tasks.find(1).orElseThrow().stale(date(10, 30), date(10, 30), false, SUN_25_OCT)).isTrue();
    }

    @Test
    void an_edited_after_done_schedule_produces_nothing_until_the_next_tick_off() {
        handler.create("Descale the kettle", null, new AfterDone(3, AfterDone.Unit.WEEKS, SUN_25_OCT));

        handler.edit(1, "Descale the kettle", null, new AfterDone(2, AfterDone.Unit.DAYS, SUN_25_OCT));
        producer.afterDone(1, SUN_25_OCT, SUN_25_OCT, SUN_25_OCT);

        assertThat(tasks.occurrences()).extracting(Occurrence::originalDueOn).containsExactly(SUN_25_OCT, date(10, 27));
        assertThat(tasks.find(1).orElseThrow().producedThrough()).isNull();
    }

    @Test
    void a_stopped_task_produces_nothing_more() {
        handler.create("Water plants", null, new Weekly(Set.of(MONDAY), 1, SUN_25_OCT));
        producer.catchUp(SUN_25_OCT);

        handler.stop(1);
        producer.catchUp(date(11, 20));

        assertThat(tasks.occurrences()).extracting(Occurrence::originalDueOn).containsExactly(date(10, 26), date(11, 2), date(11, 9));
    }

    @Test
    void switching_between_calendar_and_after_done_is_rejected() {
        handler.create("Water plants", null, new Weekly(Set.of(MONDAY), 1, SUN_25_OCT));

        assertThatThrownBy(() -> handler.edit(1, "Water plants", null, new AfterDone(1, AfterDone.Unit.WEEKS, SUN_25_OCT)))
            .isInstanceOf(RejectedException.class);
    }

    @Test
    void a_stopped_task_can_be_neither_edited_nor_stopped_again() {
        handler.create("Water plants", null, new Weekly(Set.of(MONDAY), 1, SUN_25_OCT));
        handler.stop(1);

        assertThatThrownBy(() -> handler.edit(1, "Water", null, new Weekly(Set.of(MONDAY), 1, SUN_25_OCT)))
            .isInstanceOf(RejectedException.class).hasMessage("That recurring task is stopped.");
        assertThatThrownBy(() -> handler.stop(1)).isInstanceOf(RejectedException.class);
    }

    private static LocalDate date(int month, int day) {
        return LocalDate.of(2026, month, day);
    }
}
