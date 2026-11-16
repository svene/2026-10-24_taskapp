package org.svenehrke.taskapp.board;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** The Board as the browser renders it: {@link Board#SPAN_DAYS} day Columns (Today first), then Future. */
public record BoardVM(List<DayColumnVM> days, FutureVM future) {

    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    static BoardVM of(Board board) {
        var today = board.days().getFirst().date();
        var days = board.days().stream().map(day -> DayColumnVM.of(day.date(), today)).toList();
        return new BoardVM(days, new FutureVM("from " + DAY_MONTH.format(board.future().from())));
    }

    /** {@code id}: stable DOM id of the Column. {@code date}: ISO, {@code weekday}/{@code dayMonth}: its label. */
    public record DayColumnVM(String id, String date, String weekday, String dayMonth, boolean today, boolean weekend) {

        static DayColumnVM of(LocalDate date, LocalDate today) {
            var weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            return new DayColumnVM("col-" + date, date.toString(), WEEKDAY.format(date), DAY_MONTH.format(date),
                date.equals(today), weekend);
        }
    }

    public record FutureVM(String fromLabel) {}
}
