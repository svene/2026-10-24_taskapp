package org.svenehrke.taskapp.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.svenehrke.taskapp.board.Board.BoardCard;
import java.util.List;
import org.svenehrke.taskapp.card.Card;
import org.svenehrke.taskapp.card.InMemoryCardRepository;
import org.svenehrke.taskapp.card.MutableTestClock;
import org.svenehrke.taskapp.recurring.InMemoryRecurringTaskRepository;
import org.svenehrke.taskapp.recurring.OccurrenceProducer;

class BoardHandlerTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Zurich");
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 25);

    private static final InMemoryRecurringTaskRepository NO_RECURRING_TASKS = new InMemoryRecurringTaskRepository();
    private static final OccurrenceProducer NO_RECURRING = new OccurrenceProducer(NO_RECURRING_TASKS);

    private final InMemoryCardRepository cards = new InMemoryCardRepository();
    private final BoardHandler handler =
        new BoardHandler(Clock.fixed(SUNDAY.atTime(23, 30).atZone(ZONE).toInstant(), ZONE), cards, NO_RECURRING, NO_RECURRING_TASKS);

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

    @Test
    void open_cards_due_before_today_are_overdue_in_the_box_oldest_first_then_by_position() {
        cards.insertOneOff("21 Oct b", null, LocalDate.of(2026, 10, 21), 1);
        cards.insertOneOff("24 Oct", null, LocalDate.of(2026, 10, 24), 0);
        cards.insertOneOff("20 Oct", null, LocalDate.of(2026, 10, 20), 5);
        cards.insertOneOff("21 Oct a", null, LocalDate.of(2026, 10, 21), 0);
        cards.insertOneOff("today", null, SUNDAY, 0);

        var board = handler.board();

        assertThat(board.overdue()).extracting(BoardCard::title, BoardCard::overdue).containsExactly(
            tuple("20 Oct", true),
            tuple("21 Oct a", true),
            tuple("21 Oct b", true),
            tuple("24 Oct", true));
        assertThat(board.days().getFirst().cards()).extracting(BoardCard::title, BoardCard::overdue)
            .containsExactly(tuple("today", false));
    }

    @Test
    void an_open_card_becomes_overdue_at_midnight() {
        var clock = new MutableTestClock(SUNDAY);
        var board = new BoardHandler(clock, cards, NO_RECURRING, NO_RECURRING_TASKS);
        cards.insertOneOff("Water the plants", null, SUNDAY, 0);

        clock.set(SUNDAY, LocalTime.of(23, 59, 59));
        assertThat(board.board().overdue()).isEmpty();
        assertThat(board.board().days().getFirst().cards()).extracting(BoardCard::overdue).containsExactly(false);

        clock.set(SUNDAY.plusDays(1), LocalTime.MIDNIGHT);
        assertThat(board.board().overdue()).extracting(BoardCard::title, BoardCard::overdue)
            .containsExactly(tuple("Water the plants", true));
    }

    /** The example of the slice: due 20 Oct, ticked off 25 Oct: struck through in Today on 25 Oct, gone on 26 Oct. */
    @Test
    void a_done_overdue_card_stays_in_the_box_until_the_end_of_the_day_it_was_ticked_off() {
        var clock = new MutableTestClock(SUNDAY);
        var board = new BoardHandler(clock, cards, NO_RECURRING, NO_RECURRING_TASKS);
        cards.insertOneOff("due 20 Oct", null, LocalDate.of(2026, 10, 20), 0);
        cards.setDoneOn(1, SUNDAY);

        clock.set(SUNDAY, LocalTime.of(23, 59, 59));
        assertThat(board.board().overdue()).extracting(BoardCard::title, BoardCard::done, BoardCard::overdue)
            .containsExactly(tuple("due 20 Oct", true, false));

        clock.set(SUNDAY.plusDays(1), LocalTime.MIDNIGHT);
        assertThat(board.board().overdue()).isEmpty();
        assertThat(board.board().days()).allSatisfy(day -> assertThat(day.cards()).isEmpty());
    }

    @Test
    void a_card_ticked_off_early_stays_done_on_its_day_until_that_day_is_past() {
        var clock = new MutableTestClock(SUNDAY);
        var board = new BoardHandler(clock, cards, NO_RECURRING, NO_RECURRING_TASKS);
        var tuesday = SUNDAY.plusDays(2);
        cards.insertOneOff("Book flights", null, tuesday, 0);
        cards.setDoneOn(1, SUNDAY);

        assertThat(board.board().days().get(2).cards()).extracting(BoardCard::title, BoardCard::done)
            .containsExactly(tuple("Book flights", true));

        clock.setToday(tuesday);
        assertThat(board.board().days().getFirst().cards()).extracting(BoardCard::done).containsExactly(true);

        clock.setToday(tuesday.plusDays(1));
        assertThat(board.board().overdue()).isEmpty();
        assertThat(board.board().days()).allSatisfy(day -> assertThat(day.cards()).isEmpty());
    }

    @Test
    void a_done_card_after_the_span_is_not_in_future() {
        var clock = new MutableTestClock(SUNDAY);
        cards.insertOneOff("far away", null, LocalDate.of(2026, 12, 1), 0);
        cards.setDoneOn(1, SUNDAY);

        assertThat(new BoardHandler(clock, cards, NO_RECURRING, NO_RECURRING_TASKS).board().future().nearest()).isEmpty();
    }

    /** Stale Cards never take one of Future's five nearest places; they wait behind "+N more", in Board order. */
    @Test
    void future_nearest_skips_stale_cards() {
        var from = Board.futureFrom(SUNDAY);
        var future = java.util.stream.IntStream.range(0, 7)
            .mapToObj(i -> new Card(i + 1, 1L, "t" + (i + 1), null, from.plusDays(i), from.plusDays(i), 0, null)).toList();

        var board = Board.of(SUNDAY, List.of(), List.of(), future, card -> card.id() == 2 || card.id() == 3);

        assertThat(board.future().nearest()).extracting(BoardCard::title).containsExactly("t1", "t4", "t5", "t6", "t7");
        assertThat(board.future().more()).extracting(BoardCard::title, BoardCard::stale)
            .containsExactly(tuple("t2", true), tuple("t3", true));
    }
}
