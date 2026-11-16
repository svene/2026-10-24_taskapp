package org.svenehrke.taskapp.card;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import org.svenehrke.taskapp.shared.RejectedException;
import org.svenehrke.taskapp.shared.Span;

/** Inbound port: the use cases on Cards. Commands return {@code void}; the Receiver then reads the Board. */
@ApplicationScoped
public class CardHandler {

    private final Clock clock;
    private final CardRepositorySPI cards;

    @Inject
    CardHandler(Clock clock, CardRepositorySPI cards) {
        this.clock = clock;
        this.cards = cards;
    }

    /** A new One-off Task at the bottom of its day. {@code notes} may be null. */
    @Transactional
    public void createOneOff(String title, String notes, LocalDate dueOn) {
        int position = cards.maxPosition(dueOn).orElse(-1) + 1;
        cards.insertOneOff(title, notes, dueOn, position);
    }

    /**
     * Ticks the Card off today; its due date and position stay. A Card that is already Done keeps its done date.
     *
     * @throws RejectedException for an unknown id
     */
    @Transactional
    public void tick(long id) {
        if (!existing(id).done()) cards.setDoneOn(id, LocalDate.now(clock));
    }

    /**
     * Makes the Card open again and undoes nothing else.
     *
     * @throws RejectedException for an unknown id
     */
    @Transactional
    public void untick(long id) {
        if (existing(id).done()) cards.setDoneOn(id, null);
    }

    /**
     * The Move of spec §2.5, for the card buttons, drag-and-drop and the edit modal: the Card goes to {@code target}
     * (an ISO day from today on, or {@code future}), right above the Card {@code before}, or to the bottom when
     * {@code before} is null or not on that day. The target day is renumbered densely 0..n, the source day is not.
     * Only the due date and position change; the original due date stays.
     *
     * @throws RejectedException for an unknown id, a Done Card, a past day or a target that is neither
     */
    @Transactional
    public void move(long id, String target, Long before) {
        var card = existing(id);
        if (card.done()) throw new RejectedException("A done task cannot be moved.");
        var today = LocalDate.now(clock);
        var day = switch (MoveTarget.parse(target)) {
            case MoveTarget.Day(var date) -> date;
            case MoveTarget.Future() -> Span.futureFrom(today);
        };
        if (day.isBefore(today)) throw new RejectedException("A task cannot be moved to a past day.");

        var order = new ArrayList<>(cards.dueOn(day).stream().map(Card::id).filter(other -> other != id).toList());
        int at = before == null ? -1 : order.indexOf(before);
        order.add(at < 0 ? order.size() : at, id);
        for (int position = 0; position < order.size(); position++) {
            cards.setDueOnAndPosition(order.get(position), day, position);
        }
    }

    /**
     * The One-off Task the edit modal opens with.
     *
     * @throws RejectedException for an unknown id or an Occurrence (its title and notes belong to its Recurring Task)
     */
    public Card oneOff(long id) {
        var card = existing(id);
        if (card.recurringTaskId() != null) throw new RejectedException("A recurring task's occurrence cannot be edited here.");
        return card;
    }

    /**
     * The edit modal's save (spec §3.2): a new title and notes ({@code notes} may be null). A changed {@code dueOn}
     * goes through {@link #move} to the bottom of that day, in the same transaction, so a rejected Move saves nothing.
     *
     * @throws RejectedException for an unknown id, an Occurrence, or a rejected Move (Done Card, past day)
     */
    @Transactional
    public void editOneOff(long id, String title, String notes, LocalDate dueOn) {
        var card = oneOff(id);
        if (!dueOn.equals(card.dueOn())) move(id, dueOn.toString(), null);
        cards.setTitleAndNotes(id, title, notes);
    }

    /**
     * The Card the edit modal opens with: a One-off Task, or an Occurrence (then showing its Recurring Task's title
     * and notes, which the read joins in).
     *
     * @throws RejectedException for an unknown id
     */
    @Transactional
    public Card card(long id) {
        return existing(id);
    }

    /**
     * The Occurrence edit modal's save (spec §3.2): only its due date and Done. A changed {@code dueOn} goes through
     * {@link #move} to the bottom of that day; Done is set around it (a Done Card cannot be moved), all in one
     * transaction, so a rejected Move saves nothing.
     *
     * @throws RejectedException for an unknown id, a One-off Task, or a rejected Move (past day)
     */
    @Transactional
    public void editOccurrence(long id, LocalDate dueOn, boolean done) {
        var card = existing(id);
        if (card.recurringTaskId() == null) throw new RejectedException("A one-off task is edited in its own modal.");
        if (card.done() && !done) cards.setDoneOn(id, null);
        if (!dueOn.equals(card.dueOn())) move(id, dueOn.toString(), null);
        if (done && !card.done()) cards.setDoneOn(id, LocalDate.now(clock));
    }

    private Card existing(long id) {
        return cards.find(id).orElseThrow(() -> new RejectedException("That task does not exist (any more)."));
    }
}
