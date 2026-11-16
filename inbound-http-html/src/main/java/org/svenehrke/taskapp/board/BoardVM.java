package org.svenehrke.taskapp.board;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.svenehrke.taskapp.board.Board.BoardCard;
import org.svenehrke.taskapp.card.Card;
import org.svenehrke.taskapp.recurring.AfterDone;
import org.svenehrke.taskapp.recurring.MonthlyLastDay;
import org.svenehrke.taskapp.recurring.MonthlyOnDay;
import org.svenehrke.taskapp.recurring.ParsedSchedule;
import org.svenehrke.taskapp.recurring.RecurringTask;
import org.svenehrke.taskapp.recurring.Schedule;
import org.svenehrke.taskapp.recurring.SchedulePreviewVM;
import org.svenehrke.taskapp.recurring.ScheduleReceiver;
import org.svenehrke.taskapp.recurring.Weekly;

/**
 * The Board as the browser renders it: {@link Board#SPAN_DAYS} day Columns (Today first), then Future.
 * {@code newCard} is the open New Task modal, {@code editCard} the open Edit modal of a One-off Task,
 * {@code editOccurrence} the open Edit modal of an Occurrence, each {@code null} when closed.
 * {@code notice} is why the last command was rejected, {@code null} otherwise.
 */
public record BoardVM(List<DayColumnVM> days, FutureVM future, NewCardVM newCard, EditCardVM editCard,
    EditOccurrenceVM editOccurrence, String notice) {

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
        var days = board.days().stream().map(day -> DayColumnVM.of(day, today, board.overdue(), quickAddError)).toList();
        return new BoardVM(days, FutureVM.of(board.future(), today), newCard, null, null, null);
    }

    /** The Board with the Edit modal of a One-off Task open. */
    static BoardVM of(Board board, EditCardVM editCard) {
        var vm = of(board);
        return new BoardVM(vm.days, vm.future, null, editCard, null, null);
    }

    /** The Board with the Edit modal of an Occurrence open. */
    static BoardVM of(Board board, EditOccurrenceVM editOccurrence) {
        var vm = of(board);
        return new BoardVM(vm.days, vm.future, null, null, editOccurrence, null);
    }

    /** The Board after a rejected command, with its reason as the notice. */
    static BoardVM rejected(Board board, String reason) {
        return of(board).withNotice(reason);
    }

    private BoardVM withNotice(String notice) {
        return new BoardVM(days, future, newCard, editCard, editOccurrence, notice);
    }

    /** A quick-add that was rejected: the raw {@code dueOn} of its Column and the message to show there. */
    record QuickAddError(String dueOn, String message) {}

    /**
     * {@code id}: stable DOM id of the Column. {@code date}: ISO, {@code weekday}/{@code dayMonth}: its label.
     * {@code overdue}: the Overdue box, only in Today (empty elsewhere); its Cards carry their date.
     * {@code quickAddError}: why the last quick-add in this Column was rejected, or {@code null}.
     */
    public record DayColumnVM(String id, String date, String weekday, String dayMonth, boolean today, boolean weekend,
        List<CardVM> overdue, List<CardVM> cards, String quickAddError) {

        static DayColumnVM of(Board.BoardDay day, LocalDate today, List<BoardCard> overdueBox, QuickAddError quickAddError) {
            var date = day.date();
            var weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            var error = quickAddError != null && quickAddError.dueOn().equals(date.toString())
                ? quickAddError.message() : null;
            return new DayColumnVM("col-" + date, date.toString(), WEEKDAY.format(date), DAY_MONTH.format(date),
                date.equals(today), weekend,
                date.equals(today) ? overdueBox.stream().map(card -> CardVM.of(card, today, true)).toList() : List.of(),
                day.cards().stream().map(card -> CardVM.of(card, today, false)).toList(), error);
        }
    }

    /**
     * Future: the nearest open Cards after the span ({@code nearest}), the rest behind "+N more" ({@code more}).
     * {@code from}: ISO date of its first day, the due date "+" presets.
     */
    public record FutureVM(String from, String fromLabel, List<CardVM> nearest, List<CardVM> more) {

        static FutureVM of(Board.Future future, LocalDate today) {
            return new FutureVM(future.from().toString(), "from " + DAY_MONTH.format(future.from()),
                future.nearest().stream().map(card -> CardVM.of(card, today, true)).toList(),
                future.more().stream().map(card -> CardVM.of(card, today, true)).toList());
        }
    }

    /**
     * A compact Card: its title and markers. {@code dateLabel} ("9 Nov") is set for Cards in Future and in the
     * Overdue box. {@code done}: struck through, its checkbox ticked. {@code overdue}: shows ⚠.
     * {@code originalLabel}: the original due date ("25 Oct") shown after ⇢ when it differs from the due date.
     * {@code moves}: the card buttons' targets, {@code null} for a Done Card (it cannot be moved).
     * {@code recurring}: an Occurrence, shows ↻ (its Edit modal is the Occurrence one).
     */
    public record CardVM(String id, long taskId, String title, String dateLabel, boolean done, boolean overdue,
        String originalLabel, MovesVM moves, boolean recurring) {

        static CardVM of(BoardCard card, LocalDate today, boolean withDate) {
            return new CardVM("card-" + card.id(), card.id(), card.title(),
                withDate ? DAY_MONTH.format(card.dueOn()) : null, card.done(), card.overdue(),
                card.rescheduled() ? DAY_MONTH.format(card.originalDueOn()) : null,
                card.done() ? null : MovesVM.of(card, today), card.recurringTaskId() != null);
        }
    }

    /**
     * The card buttons' absolute targets (ISO dates), posted as {@code target} of a Move. {@code toToday}: only for
     * an Overdue Card, else {@code null}. {@code plusDay} / {@code plusWeek} count from the due date, or from today
     * for an Overdue Card (its due date is past, so a day later could still be past).
     */
    public record MovesVM(String toToday, String plusDay, String plusWeek) {

        static MovesVM of(BoardCard card, LocalDate today) {
            var from = card.overdue() ? today : card.dueOn();
            return new MovesVM(card.overdue() ? today.toString() : null,
                from.plusDays(1).toString(), from.plusWeeks(1).toString());
        }
    }

    /**
     * The New Task modal: the values typed so far and the message per field. {@code repeats}: {@code once} (a One-off
     * Task, due {@code dueOn}), {@code calendar} (a Recurring Task of Schedule {@code kind} from {@code startsOn}:
     * {@code WEEKLY} on {@code weekdays} as ISO numbers 1 = Monday .. 7 = Sunday, every {@code everyWeeks} weeks;
     * {@code MONTHLY_DAY} on day {@code monthDay}; {@code MONTHLY_LAST} on the last day of the month) or
     * {@code afterDone} (a Recurring Task due on {@code startsOn}, then {@code afterAmount} {@code afterUnit}
     * ({@code DAYS} or {@code WEEKS}) after each time it is done). {@code preview}: the first dates of that Schedule,
     * refreshed by {@code /schedules/preview} while typing.
     */
    public record NewCardVM(String repeats, String title, String notes, String dueOn, String kind, List<Integer> weekdays,
        String everyWeeks, String monthDay, String startsOn, String afterAmount, String afterUnit,
        Map<String, String> errors, SchedulePreviewVM preview) {

        public static final String ONCE = "once";
        public static final String CALENDAR = "calendar";
        public static final String AFTER_DONE = ScheduleReceiver.AFTER_DONE;

        /**
         * A One-off Task due {@code dueOn}; switched to a calendar, it repeats from today on that weekday (Weekly) or
         * on that day of the month (Monthly on day, at most 28).
         */
        static NewCardVM empty(String dueOn, LocalDate today) {
            var day = Objects.requireNonNullElse(parseOrNull(dueOn), today);
            var weekdays = List.of(day.getDayOfWeek().getValue());
            var monthDay = String.valueOf(Math.min(day.getDayOfMonth(), 28));
            var input = new ParsedSchedule.Input(ParsedSchedule.WEEKLY, List.of(String.valueOf(weekdays.getFirst())), "1",
                monthDay, today.toString(), "1", "WEEKS");
            return new NewCardVM(ONCE, "", "", dueOn, ParsedSchedule.WEEKLY, weekdays, "1", monthDay, today.toString(),
                "1", "WEEKS", Map.of(), SchedulePreviewVM.of(input));
        }

        private static LocalDate parseOrNull(String date) {
            try {
                return LocalDate.parse(date);
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    /**
     * The Edit modal of a One-off Task (spec §3.2): {@code id} of the Card, the values shown (stored, or as typed when
     * rejected) and the message per field. {@code minDueOn}: the date picker's min, today, or the due date itself when
     * that is earlier (an Overdue Card can keep its day).
     */
    public record EditCardVM(long id, String title, String notes, String dueOn, String minDueOn, Map<String, String> errors) {

        static EditCardVM of(Card card, LocalDate today) {
            return new EditCardVM(card.id(), card.title(), Objects.requireNonNullElse(card.notes(), ""),
                card.dueOn().toString(), minDueOn(card, today), Map.of());
        }

        static EditCardVM rejected(Card card, LocalDate today, String title, String notes, String dueOn,
            Map<String, String> errors) {
            return new EditCardVM(card.id(), title, notes, dueOn, minDueOn(card, today), errors);
        }

        static String minDueOn(Card card, LocalDate today) {
            return (card.dueOn().isBefore(today) ? card.dueOn() : today).toString();
        }
    }

    /**
     * The Edit modal of an Occurrence (spec §3.2): only {@code dueOn} (a Move, the date picker's min as for
     * {@link EditCardVM}) and {@code done} change. {@code title}, {@code notes} and {@code schedule} (a sentence)
     * are its Recurring Task's, shown read-only. {@code errors}: the message per field ({@code dueOn}).
     */
    public record EditOccurrenceVM(long id, String title, String notes, String schedule, String dueOn, String minDueOn,
        boolean done, Map<String, String> errors) {

        private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

        static EditOccurrenceVM of(Card card, RecurringTask task, LocalDate today) {
            return new EditOccurrenceVM(card.id(), task.title(), Objects.requireNonNullElse(task.notes(), ""),
                describe(task.schedule()), card.dueOn().toString(), EditCardVM.minDueOn(card, today), card.done(), Map.of());
        }

        static EditOccurrenceVM rejected(Card card, RecurringTask task, LocalDate today, String dueOn, boolean done,
            Map<String, String> errors) {
            var vm = of(card, task, today);
            return new EditOccurrenceVM(vm.id, vm.title, vm.notes, vm.schedule, dueOn, vm.minDueOn, done, errors);
        }

        /**
         * "Every week on Mon, Thu, from 25 Oct 2026"; "Every 2 weeks on Sun, from ..."; "Every month on day 5, from ...";
         * "Every month on the last day, from ..."; "Every 3 weeks after done, from ...".
         */
        static String describe(Schedule schedule) {
            return switch (schedule) {
                case Weekly weekly -> (weekly.everyWeeks() == 1 ? "Every week" : "Every " + weekly.everyWeeks() + " weeks")
                    + " on " + weekly.weekdays().stream().sorted().map(WEEKDAY::format).collect(Collectors.joining(", "))
                    + ", from " + DAY_MONTH_YEAR.format(weekly.startsOn());
                case MonthlyOnDay monthly -> "Every month on day " + monthly.day() + ", from "
                    + DAY_MONTH_YEAR.format(monthly.startsOn());
                case MonthlyLastDay monthly -> "Every month on the last day, from " + DAY_MONTH_YEAR.format(monthly.startsOn());
                case AfterDone afterDone -> "Every " + (afterDone.amount() == 1 ? "" : afterDone.amount() + " ")
                    + SchedulePreviewVM.unit(afterDone) + " after done, from " + DAY_MONTH_YEAR.format(afterDone.startsOn());
            };
        }
    }
}
