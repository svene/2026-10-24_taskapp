package org.svenehrke.taskapp.board;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/**
 * The rolling calendar the owner sees: {@link #SPAN_DAYS} day Columns from today, then Future.
 * {@code days().getFirst()} is Today.
 */
public record Board(List<BoardDay> days, Future future) {

    /** The span: Today plus 13 more days. */
    public static final int SPAN_DAYS = 14;

    public Board {
        days = List.copyOf(days);
    }

    static Board startingOn(LocalDate today) {
        var days = Stream.iterate(today, day -> day.plusDays(1)).limit(SPAN_DAYS).map(BoardDay::new).toList();
        return new Board(days, new Future(today.plusDays(SPAN_DAYS)));
    }

    public record BoardDay(LocalDate date) {}

    /** Everything due on or after {@code from}, the day after the span. */
    public record Future(LocalDate from) {}
}
