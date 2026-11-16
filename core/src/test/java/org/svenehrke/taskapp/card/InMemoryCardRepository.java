package org.svenehrke.taskapp.card;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    public List<Card> openDueFrom(LocalDate from) {
        return rows.stream().filter(card -> !card.done() && !card.dueOn().isBefore(from)).sorted(BOARD_ORDER).toList();
    }
}
