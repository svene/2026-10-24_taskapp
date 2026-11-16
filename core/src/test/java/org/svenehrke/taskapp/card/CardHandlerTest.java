package org.svenehrke.taskapp.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CardHandlerTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 26);
    private static final LocalDate TUE = MON.plusDays(1);

    private final InMemoryCardRepository cards = new InMemoryCardRepository();
    private final CardHandler handler = new CardHandler(cards);

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
}
