package org.svenehrke.taskapp.card;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** {@link CardRepositorySPI} over a list, for unit tests of core. */
public class InMemoryCardRepository implements CardRepositorySPI {

    private static final Comparator<Card> BOARD_ORDER =
        Comparator.comparing(Card::dueOn).thenComparingInt(Card::position).thenComparingLong(Card::id);

    private final List<Card> rows = new ArrayList<>();

    public List<Card> all() {
        return rows.stream().sorted(BOARD_ORDER).toList();
    }

    @Override
    public Optional<Card> find(long id) {
        return rows.stream().filter(card -> card.id() == id).findFirst();
    }

    @Override
    public void setDoneOn(long id, LocalDate doneOn) {
        rows.replaceAll(c -> c.id() != id ? c
            : new Card(c.id(), c.recurringTaskId(), c.title(), c.notes(), c.dueOn(), c.originalDueOn(), c.position(), doneOn));
    }

    @Override
    public List<Card> dueOn(LocalDate day) {
        return rows.stream().filter(card -> card.dueOn().equals(day)).sorted(BOARD_ORDER).toList();
    }

    @Override
    public void setDueOnAndPosition(long id, LocalDate dueOn, int position) {
        rows.replaceAll(c -> c.id() != id ? c
            : new Card(c.id(), c.recurringTaskId(), c.title(), c.notes(), dueOn, c.originalDueOn(), position, c.doneOn()));
    }

    @Override
    public void setTitleAndNotes(long id, String title, String notes) {
        rows.replaceAll(c -> c.id() != id ? c
            : new Card(c.id(), c.recurringTaskId(), title, notes, c.dueOn(), c.originalDueOn(), c.position(), c.doneOn()));
    }

    /** An Occurrence of Recurring Task {@code recurringTaskId}, for tests that need one before Recurring Tasks exist. */
    public void insertOccurrence(long recurringTaskId, LocalDate dueOn, int position) {
        rows.add(new Card(rows.size() + 1, recurringTaskId, null, null, dueOn, dueOn, position, null));
    }

    @Override
    public OptionalInt maxPosition(LocalDate dueOn) {
        return rows.stream().filter(card -> card.dueOn().equals(dueOn)).mapToInt(Card::position).max();
    }

    @Override
    public void insertOneOff(String title, String notes, LocalDate dueOn, int position) {
        rows.add(new Card(rows.size() + 1, null, title, notes, dueOn, dueOn, position, null));
    }

    @Override
    public List<Card> dueBetween(LocalDate from, LocalDate through) {
        return rows.stream().filter(card -> !card.dueOn().isBefore(from) && !card.dueOn().isAfter(through))
            .sorted(BOARD_ORDER).toList();
    }

    @Override
    public List<Card> overdueBox(LocalDate today) {
        return rows.stream()
            .filter(card -> card.dueOn().isBefore(today) && (!card.done() || !card.doneOn().isBefore(today)))
            .sorted(BOARD_ORDER).toList();
    }

    @Override
    public List<Card> openDueFrom(LocalDate from) {
        return rows.stream().filter(card -> !card.done() && !card.dueOn().isBefore(from)).sorted(BOARD_ORDER).toList();
    }
}
