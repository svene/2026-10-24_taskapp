package org.svenehrke.taskapp.board;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.svenehrke.taskapp.board.Board.BoardCard;

/**
 * The Board as the browser renders it: {@link Board#SPAN_DAYS} day Columns (Today first), then Future.
 * {@code newCard} is the open New Task modal, {@code null} when it is closed.
 */
public record BoardVM(List<DayColumnVM> days, FutureVM future, NewCardVM newCard) {

    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    static BoardVM of(Board board) {
        return of(board, null, null);
    }

    /**
     * @param newCard       the modal to show, or {@code null}
     * @param quickAddError the rejected quick-add (its day and message), or {@code null}
     */
    static BoardVM of(Board board, NewCardVM newCard, QuickAddError quickAddError) {
        var today = board.days().getFirst().date();
        var days = board.days().stream().map(day -> DayColumnVM.of(day, today, quickAddError)).toList();
        return new BoardVM(days, FutureVM.of(board.future()), newCard);
    }

    /** A quick-add that was rejected: the raw {@code dueOn} of its Column and the message to show there. */
    record QuickAddError(String dueOn, String message) {}

    /**
     * {@code id}: stable DOM id of the Column. {@code date}: ISO, {@code weekday}/{@code dayMonth}: its label.
     * {@code quickAddError}: why the last quick-add in this Column was rejected, or {@code null}.
     */
    public record DayColumnVM(String id, String date, String weekday, String dayMonth, boolean today, boolean weekend,
        List<CardVM> cards, String quickAddError) {

        static DayColumnVM of(Board.BoardDay day, LocalDate today, QuickAddError quickAddError) {
            var date = day.date();
            var weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            var error = quickAddError != null && quickAddError.dueOn().equals(date.toString())
                ? quickAddError.message() : null;
            return new DayColumnVM("col-" + date, date.toString(), WEEKDAY.format(date), DAY_MONTH.format(date),
                date.equals(today), weekend, day.cards().stream().map(card -> CardVM.of(card, false)).toList(), error);
        }
    }

    /**
     * Future: the nearest open Cards after the span ({@code nearest}), the rest behind "+N more" ({@code more}).
     * {@code from}: ISO date of its first day, the due date "+" presets.
     */
    public record FutureVM(String from, String fromLabel, List<CardVM> nearest, List<CardVM> more) {

        static FutureVM of(Board.Future future) {
            return new FutureVM(future.from().toString(), "from " + DAY_MONTH.format(future.from()),
                future.nearest().stream().map(card -> CardVM.of(card, true)).toList(),
                future.more().stream().map(card -> CardVM.of(card, true)).toList());
        }
    }

    /** A compact Card: its title only. {@code dateLabel} ("9 Nov") is set for Cards in Future. */
    public record CardVM(String id, long taskId, String title, String dateLabel) {

        static CardVM of(BoardCard card, boolean withDate) {
            return new CardVM("card-" + card.id(), card.id(), card.title(),
                withDate ? DAY_MONTH.format(card.dueOn()) : null);
        }
    }

    /** The New Task modal: the values typed so far and the message per field ({@code title}, {@code dueOn}). */
    public record NewCardVM(String title, String notes, String dueOn, Map<String, String> errors) {

        static NewCardVM empty(String dueOn) {
            return new NewCardVM("", "", dueOn, Map.of());
        }
    }
}
