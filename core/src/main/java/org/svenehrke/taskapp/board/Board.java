package org.svenehrke.taskapp.board;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.svenehrke.taskapp.card.Card;
import org.svenehrke.taskapp.shared.Span;

/**
 * The rolling calendar the owner sees: {@link #SPAN_DAYS} day Columns from today, then Future.
 * {@code days().getFirst()} is Today; {@code overdue} is the Overdue box at its top.
 */
public record Board(List<BoardDay> days, List<BoardCard> overdue, Future future) {

    /** The span: Today plus 13 more days ({@link Span#DAYS}). */
    public static final int SPAN_DAYS = Span.DAYS;

    /** How many Cards Future shows before "+N more". */
    public static final int FUTURE_NEAREST = 5;

    public Board {
        days = List.copyOf(days);
        overdue = List.copyOf(overdue);
    }

    /** The day after the span, where Future starts. */
    static LocalDate futureFrom(LocalDate today) {
        return Span.futureFrom(today);
    }

    /**
     * @param overdueBox   the Cards of the Overdue box (see {@code CardRepositorySPI.overdueBox}), in Board order
     * @param inSpan       the Cards due within the span, ordered by due date, position, id
     * @param openInFuture the open Cards due from {@link #futureFrom} on, in the same order
     * @param stale        which Occurrences are Stale (spec §2.4); Stale Cards never take one of Future's nearest places
     */
    static Board of(LocalDate today, List<Card> overdueBox, List<Card> inSpan, List<Card> openInFuture, Predicate<Card> stale) {
        Map<LocalDate, List<BoardCard>> byDay = inSpan.stream()
            .collect(Collectors.groupingBy(Card::dueOn, Collectors.mapping(card -> BoardCard.of(card, today, stale.test(card)), Collectors.toList())));
        var days = Stream.iterate(today, day -> day.plusDays(1)).limit(SPAN_DAYS)
            .map(day -> new BoardDay(day, byDay.getOrDefault(day, List.of())))
            .toList();
        var future = openInFuture.stream().map(card -> BoardCard.of(card, today, stale.test(card))).toList();
        var nearest = future.stream().filter(card -> !card.stale()).limit(FUTURE_NEAREST).toList();
        var more = future.stream().filter(card -> !nearest.contains(card)).toList();
        var overdue = overdueBox.stream().map(card -> BoardCard.of(card, today, stale.test(card))).toList();
        return new Board(days, overdue, new Future(futureFrom(today), nearest, more));
    }

    /** One day Column; its Cards in Board order (position, id). */
    public record BoardDay(LocalDate date, List<BoardCard> cards) {
        public BoardDay {
            cards = List.copyOf(cards);
        }
    }

    /**
     * Everything open and due on or after {@code from}, the day after the span: the {@link #FUTURE_NEAREST} nearest
     * Cards that are not Stale, and the rest (Stale ones included) behind "+N more", both in Board order.
     */
    public record Future(LocalDate from, List<BoardCard> nearest, List<BoardCard> more) {
        public Future {
            nearest = List.copyOf(nearest);
            more = List.copyOf(more);
        }
    }

    /**
     * A Card as the Board shows it, with what is derived from the facts: {@code overdue} = due before today and not
     * Done; {@code stale} see {@code RecurringTask.stale}. {@code recurringTaskId} is null for a One-off Task.
     */
    public record BoardCard(
        long id,
        String title,
        LocalDate dueOn,
        LocalDate originalDueOn,
        boolean done,
        boolean overdue,
        boolean stale,
        boolean rescheduled,
        Long recurringTaskId
    ) {
        static BoardCard of(Card card, LocalDate today, boolean stale) {
            return new BoardCard(card.id(), card.title(), card.dueOn(), card.originalDueOn(), card.done(),
                !card.done() && card.dueOn().isBefore(today), stale, !card.dueOn().equals(card.originalDueOn()),
                card.recurringTaskId());
        }
    }
}
