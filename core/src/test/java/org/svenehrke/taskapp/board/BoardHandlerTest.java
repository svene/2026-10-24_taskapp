package org.svenehrke.taskapp.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.svenehrke.taskapp.board.Board.BoardCard;
import org.svenehrke.taskapp.card.InMemoryCardRepository;

class BoardHandlerTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Zurich");
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 25);

    private final InMemoryCardRepository cards = new InMemoryCardRepository();
    private final BoardHandler handler =
        new BoardHandler(Clock.fixed(SUNDAY.atTime(23, 30).atZone(ZONE).toInstant(), ZONE), cards);

    @Test
    void board_spans_fourteen_days_from_today_then_future() {
        var board = handler.board();

        assertThat(board.days()).hasSize(Board.SPAN_DAYS);
        assertThat(board.days().getFirst().date()).isEqualTo(SUNDAY);
        assertThat(board.days().getLast().date()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(board.future().from()).isEqualTo(LocalDate.of(2026, 11, 8));
    }

    @Test
    void cards_appear_on_their_day_in_position_order() {
        cards.insertOneOff("second", null, SUNDAY.plusDays(1), 1);
        cards.insertOneOff("first", null, SUNDAY.plusDays(1), 0);
        cards.insertOneOff("last day of the span", null, LocalDate.of(2026, 11, 7), 0);

        var board = handler.board();

        assertThat(board.days().get(1).cards()).extracting(BoardCard::title).containsExactly("first", "second");
        assertThat(board.days().getLast().cards()).extracting(BoardCard::title).containsExactly("last day of the span");
        assertThat(board.future().nearest()).isEmpty();
    }

    @Test
    void equal_positions_are_ordered_by_id() {
        cards.insertOneOff("older", null, SUNDAY, 0);
        cards.insertOneOff("newer", null, SUNDAY, 0);

        assertThat(handler.board().days().getFirst().cards()).extracting(BoardCard::title)
            .containsExactly("older", "newer");
    }

    /** The example of the slice: today 25 Oct, Cards due 8, 9, 9, 12, 20 and 30 Nov. */
    @Test
    void future_shows_the_five_nearest_open_cards_then_more() {
        cards.insertOneOff("30 Nov", null, LocalDate.of(2026, 11, 30), 0);
        cards.insertOneOff("9 Nov b", null, LocalDate.of(2026, 11, 9), 1);
        cards.insertOneOff("12 Nov", null, LocalDate.of(2026, 11, 12), 0);
        cards.insertOneOff("8 Nov", null, LocalDate.of(2026, 11, 8), 0);
        cards.insertOneOff("20 Nov", null, LocalDate.of(2026, 11, 20), 0);
        cards.insertOneOff("9 Nov a", null, LocalDate.of(2026, 11, 9), 0);

        var future = handler.board().future();

        assertThat(future.nearest()).extracting(BoardCard::title, BoardCard::dueOn).containsExactly(
            tuple("8 Nov", LocalDate.of(2026, 11, 8)),
            tuple("9 Nov a", LocalDate.of(2026, 11, 9)),
            tuple("9 Nov b", LocalDate.of(2026, 11, 9)),
            tuple("12 Nov", LocalDate.of(2026, 11, 12)),
            tuple("20 Nov", LocalDate.of(2026, 11, 20)));
        assertThat(future.more()).extracting(BoardCard::title).containsExactly("30 Nov");
    }

    @Test
    void a_new_one_off_task_is_neither_overdue_nor_rescheduled() {
        cards.insertOneOff("Water the plants", null, SUNDAY, 0);

        assertThat(handler.board().days().getFirst().cards()).singleElement().satisfies(card -> {
            assertThat(card.overdue()).isFalse();
            assertThat(card.rescheduled()).isFalse();
            assertThat(card.done()).isFalse();
            assertThat(card.recurringTaskId()).isNull();
        });
    }
}
