package org.svenehrke.taskapp.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.svenehrke.taskapp.shared.RejectedException;

class CardHandlerTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 26);
    private static final LocalDate TUE = MON.plusDays(1);

    private final InMemoryCardRepository cards = new InMemoryCardRepository();
    private final MutableTestClock clock = new MutableTestClock(MON);
    private final CardHandler handler = new CardHandler(clock, cards);

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
}
