package org.svenehrke.taskapp.card;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import org.svenehrke.taskapp.shared.RejectedException;

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

    private Card existing(long id) {
        return cards.find(id).orElseThrow(() -> new RejectedException("That task does not exist (any more)."));
    }
}
