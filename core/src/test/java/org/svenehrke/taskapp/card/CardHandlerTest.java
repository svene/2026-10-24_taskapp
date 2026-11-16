package org.svenehrke.taskapp.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.svenehrke.taskapp.recurring.AfterDone;
import org.svenehrke.taskapp.recurring.InMemoryRecurringTaskRepository;
import org.svenehrke.taskapp.recurring.InMemoryRecurringTaskRepository.Occurrence;
import org.svenehrke.taskapp.recurring.OccurrenceProducer;
import org.svenehrke.taskapp.shared.RejectedException;

class CardHandlerTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 26);
    private static final LocalDate TUE = MON.plusDays(1);

    private final InMemoryCardRepository cards = new InMemoryCardRepository();
    private final MutableTestClock clock = new MutableTestClock(MON);
    private final InMemoryRecurringTaskRepository recurringTasks = new InMemoryRecurringTaskRepository();
    private final CardHandler handler = new CardHandler(clock, cards, new OccurrenceProducer(recurringTasks));

    @Test
    void creates_a_one_off_task_whose_original_due_date_is_its_due_date() {
        handler.createOneOff("Water the plants", "the big ones", MON);

        assertThat(cards.all()).singleElement().satisfies(card -> {
            assertThat(card.recurringTaskId()).isNull();
            assertThat(card.title()).isEqualTo("Water the plants");
            assertThat(card.notes()).isEqualTo("the big ones");
            assertThat(card.dueOn()).isEqualTo(MON);
            assertThat(card.originalDueOn()).isEqualTo(MON);
            assertThat(card.position()).isZero();
            assertThat(card.done()).isFalse();
        });
    }

    @Test
    void a_new_card_goes_to_the_bottom_of_its_day() {
        handler.createOneOff("first", null, MON);
        handler.createOneOff("other day", null, TUE);
        handler.createOneOff("second", null, MON);

        assertThat(cards.all()).extracting(Card::title, Card::dueOn, Card::position).containsExactly(
            tuple("first", MON, 0),
            tuple("second", MON, 1),
            tuple("other day", TUE, 0));
    }

    @Test
    void the_bottom_is_max_position_plus_one_even_with_gaps() {
        cards.insertOneOff("moved here", null, MON, 4);

        handler.createOneOff("new", null, MON);

        assertThat(cards.all()).extracting(Card::title, Card::position)
            .containsExactly(tuple("moved here", 4), tuple("new", 5));
    }

    @Test
    void tick_marks_the_card_done_today_and_keeps_its_day_and_position() {
        cards.insertOneOff("Water the plants", null, TUE, 3);

        handler.tick(1);

        assertThat(cards.all()).extracting(Card::doneOn, Card::dueOn, Card::position).containsExactly(tuple(MON, TUE, 3));
    }

    @Test
    void ticking_a_done_card_again_keeps_the_day_it_was_ticked_off() {
        cards.insertOneOff("Water the plants", null, MON, 0);
        handler.tick(1);
        clock.setToday(TUE);

        handler.tick(1);

        assertThat(cards.all()).extracting(Card::doneOn).containsExactly(MON);
    }

    @Test
    void ticking_off_an_after_done_occurrence_produces_its_successor() {
        recurringTasks.insert("Descale the kettle", null, new AfterDone(1, AfterDone.Unit.WEEKS, MON), null);
        cards.insertOccurrence(1, MON, 0);
        clock.setToday(TUE);

        handler.tick(1);

        assertThat(recurringTasks.occurrences()).containsExactly(new Occurrence(1, TUE.plusWeeks(1)));
    }

    @Test
    void saving_an_after_done_occurrence_as_done_produces_its_successor() {
        recurringTasks.insert("Descale the kettle", null, new AfterDone(2, AfterDone.Unit.DAYS, MON), null);
        cards.insertOccurrence(1, MON, 0);

        handler.editOccurrence(1, MON, true);

        assertThat(recurringTasks.occurrences()).containsExactly(new Occurrence(1, MON.plusDays(2)));
    }

    @Test
    void untick_makes_the_card_open_again_and_undoes_nothing_else() {
        cards.insertOneOff("Water the plants", null, TUE, 2);
        handler.tick(1);

        handler.untick(1);

        assertThat(cards.all()).extracting(Card::done, Card::doneOn, Card::dueOn, Card::position)
            .containsExactly(tuple(false, null, TUE, 2));
    }

    @Test
    void tick_and_untick_reject_an_unknown_id() {
        assertThatThrownBy(() -> handler.tick(42)).isInstanceOf(RejectedException.class)
            .hasMessage("That task does not exist (any more).");
        assertThatThrownBy(() -> handler.untick(42)).isInstanceOf(RejectedException.class);
    }

    @Test
    void move_to_another_day_goes_to_the_bottom_and_keeps_the_original_due_date() {
        cards.insertOneOff("Water the plants", null, MON, 0);
        cards.insertOneOff("Buy stamps", null, MON.plusWeeks(1), 0);

        handler.move(1, MON.plusWeeks(1).toString(), null);

        assertThat(cards.all()).extracting(Card::title, Card::dueOn, Card::originalDueOn, Card::position).containsExactly(
            tuple("Buy stamps", MON.plusWeeks(1), MON.plusWeeks(1), 0),
            tuple("Water the plants", MON.plusWeeks(1), MON, 1));
    }

    @Test
    void move_before_a_card_renumbers_the_target_day_densely_and_leaves_the_source_day_alone() {
        cards.insertOneOff("a", null, TUE, 3);
        cards.insertOneOff("b", null, TUE, 7);
        cards.insertOneOff("c", null, TUE, 9);
        cards.insertOneOff("moved", null, MON, 1);
        cards.insertOneOff("stays", null, MON, 5);

        handler.move(4, TUE.toString(), 2L);

        assertThat(cards.all()).extracting(Card::title, Card::dueOn, Card::position).containsExactly(
            tuple("stays", MON, 5),
            tuple("a", TUE, 0),
            tuple("moved", TUE, 1),
            tuple("b", TUE, 2),
            tuple("c", TUE, 3));
    }

    @Test
    void move_within_a_day_reorders_it() {
        cards.insertOneOff("a", null, MON, 0);
        cards.insertOneOff("b", null, MON, 1);
        cards.insertOneOff("c", null, MON, 2);

        handler.move(3, MON.toString(), 1L);

        assertThat(cards.all()).extracting(Card::title, Card::position)
            .containsExactly(tuple("c", 0), tuple("a", 1), tuple("b", 2));
    }

    @Test
    void move_before_a_card_of_another_day_goes_to_the_bottom() {
        cards.insertOneOff("a", null, TUE, 0);
        cards.insertOneOff("elsewhere", null, MON, 0);
        cards.insertOneOff("moved", null, MON, 1);

        handler.move(3, TUE.toString(), 2L);

        assertThat(cards.all()).extracting(Card::title, Card::dueOn, Card::position).containsExactly(
            tuple("elsewhere", MON, 0),
            tuple("a", TUE, 0),
            tuple("moved", TUE, 1));
    }

    @Test
    void open_cards_can_be_moved_above_done_ones_which_keep_their_order() {
        cards.insertOneOff("done", null, TUE, 0);
        cards.setDoneOn(1, MON);
        cards.insertOneOff("moved", null, MON, 0);

        handler.move(2, TUE.toString(), 1L);

        assertThat(cards.all()).extracting(Card::title, Card::position, Card::done)
            .containsExactly(tuple("moved", 0, false), tuple("done", 1, true));
    }

    @Test
    void move_to_future_means_the_bottom_of_the_day_after_the_span() {
        var afterSpan = MON.plusDays(14);
        cards.insertOneOff("already there", null, afterSpan, 0);
        cards.insertOneOff("moved", null, MON, 0);

        handler.move(2, "future", null);

        assertThat(cards.all()).extracting(Card::title, Card::dueOn, Card::position)
            .containsExactly(tuple("already there", afterSpan, 0), tuple("moved", afterSpan, 1));
    }

    @Test
    void moving_back_to_the_original_day_makes_due_and_original_due_date_equal_again() {
        cards.insertOneOff("Water the plants", null, MON, 0);
        handler.move(1, TUE.toString(), null);

        handler.move(1, MON.toString(), null);

        assertThat(cards.all()).extracting(Card::dueOn, Card::originalDueOn).containsExactly(tuple(MON, MON));
    }

    @Test
    void an_overdue_card_can_be_moved_to_today() {
        cards.insertOneOff("Pay the bill", null, MON.minusDays(6), 0);

        handler.move(1, MON.toString(), null);

        assertThat(cards.all()).extracting(Card::dueOn, Card::originalDueOn).containsExactly(tuple(MON, MON.minusDays(6)));
    }

    @Test
    void move_rejects_a_past_day_a_done_card_an_unknown_id_and_a_target_that_is_no_day() {
        cards.insertOneOff("open", null, MON, 0);
        cards.insertOneOff("done", null, MON, 1);
        cards.setDoneOn(2, MON);

        assertThatThrownBy(() -> handler.move(1, MON.minusDays(1).toString(), null))
            .isInstanceOf(RejectedException.class).hasMessage("A task cannot be moved to a past day.");
        assertThatThrownBy(() -> handler.move(2, TUE.toString(), null))
            .isInstanceOf(RejectedException.class).hasMessage("A done task cannot be moved.");
        assertThatThrownBy(() -> handler.move(42, TUE.toString(), null))
            .isInstanceOf(RejectedException.class).hasMessage("That task does not exist (any more).");
        assertThatThrownBy(() -> handler.move(1, "someday", null))
            .isInstanceOf(RejectedException.class).hasMessage("A task can only be moved to a day or to Future.");
        assertThat(cards.all()).extracting(Card::dueOn, Card::position).containsExactly(tuple(MON, 0), tuple(MON, 1));
    }

    @Test
    void edit_changes_title_and_notes_and_keeps_day_and_position() {
        cards.insertOneOff("Water plants", "the big ones", TUE, 2);

        handler.editOneOff(1, "Water the plants", null, TUE);

        assertThat(cards.all()).extracting(Card::title, Card::notes, Card::dueOn, Card::originalDueOn, Card::position)
            .containsExactly(tuple("Water the plants", null, TUE, TUE, 2));
    }

    @Test
    void edit_with_a_new_due_date_moves_the_card_to_the_bottom_of_that_day() {
        cards.insertOneOff("already there", null, TUE, 0);
        cards.insertOneOff("Water the plants", null, MON, 0);

        handler.editOneOff(2, "Water the plants", null, TUE);

        assertThat(cards.all()).extracting(Card::title, Card::dueOn, Card::originalDueOn, Card::position).containsExactly(
            tuple("already there", TUE, TUE, 0),
            tuple("Water the plants", TUE, MON, 1));
    }

    @Test
    void edit_rejects_an_occurrence_and_an_unknown_id() {
        cards.insertOccurrence(7, MON, 0);

        assertThatThrownBy(() -> handler.editOneOff(1, "title", null, MON))
            .isInstanceOf(RejectedException.class).hasMessage("A recurring task's occurrence cannot be edited here.");
        assertThatThrownBy(() -> handler.oneOff(1)).isInstanceOf(RejectedException.class);
        assertThatThrownBy(() -> handler.editOneOff(42, "title", null, MON))
            .isInstanceOf(RejectedException.class).hasMessage("That task does not exist (any more).");
        assertThat(cards.all()).extracting(Card::title).containsExactly((String) null);
    }

    @Test
    void edit_whose_move_is_rejected_does_not_change_the_title() {
        cards.insertOneOff("Water the plants", null, MON, 0);
        handler.tick(1);

        assertThatThrownBy(() -> handler.editOneOff(1, "renamed", null, TUE))
            .isInstanceOf(RejectedException.class).hasMessage("A done task cannot be moved.");
        assertThatThrownBy(() -> handler.editOneOff(1, "renamed", null, MON.minusDays(1)))
            .isInstanceOf(RejectedException.class);
        assertThat(cards.all()).extracting(Card::title, Card::dueOn).containsExactly(tuple("Water the plants", MON));
    }

    @Test
    void edit_of_a_done_card_keeping_its_day_changes_the_title() {
        cards.insertOneOff("Water plants", null, MON, 0);
        handler.tick(1);

        handler.editOneOff(1, "Water the plants", null, MON);

        assertThat(cards.all()).extracting(Card::title, Card::done).containsExactly(tuple("Water the plants", true));
    }

    @Test
    void edit_of_an_occurrence_moves_it_and_ticks_it_off() {
        cards.insertOneOff("already there", null, TUE, 0);
        cards.insertOccurrence(7, MON, 0);

        handler.editOccurrence(2, TUE, true);

        assertThat(cards.all()).extracting(Card::id, Card::dueOn, Card::originalDueOn, Card::position, Card::doneOn)
            .containsExactly(tuple(1L, TUE, TUE, 0, null), tuple(2L, TUE, MON, 1, MON));
    }

    /** Unticked first, so a Done Occurrence can move again. */
    @Test
    void edit_of_a_done_occurrence_unticks_it_and_moves_it() {
        cards.insertOccurrence(7, MON, 0);
        handler.tick(1);

        handler.editOccurrence(1, TUE, false);

        assertThat(cards.all()).extracting(Card::dueOn, Card::doneOn).containsExactly(tuple(TUE, null));
    }

    @Test
    void edit_of_an_occurrence_rejects_a_one_off_task_and_a_past_day() {
        cards.insertOneOff("Water plants", null, MON, 0);
        cards.insertOccurrence(7, MON, 0);

        assertThatThrownBy(() -> handler.editOccurrence(1, MON, true))
            .isInstanceOf(RejectedException.class).hasMessage("A one-off task is edited in its own modal.");
        assertThatThrownBy(() -> handler.editOccurrence(2, MON.minusDays(1), true))
            .isInstanceOf(RejectedException.class).hasMessage("A task cannot be moved to a past day.");
        assertThat(cards.all()).extracting(Card::dueOn, Card::doneOn).containsExactly(tuple(MON, null), tuple(MON, null));
    }
}
