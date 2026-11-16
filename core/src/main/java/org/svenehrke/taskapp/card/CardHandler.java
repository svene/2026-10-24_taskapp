package org.svenehrke.taskapp.card;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;

/** Inbound port: the use cases on Cards. Commands return {@code void}; the Receiver then reads the Board. */
@ApplicationScoped
public class CardHandler {

    private final CardRepositorySPI cards;

    @Inject
    CardHandler(CardRepositorySPI cards) {
        this.cards = cards;
    }

    /** A new One-off Task at the bottom of its day. {@code notes} may be null. */
    @Transactional
    public void createOneOff(String title, String notes, LocalDate dueOn) {
        int position = cards.maxPosition(dueOn).orElse(-1) + 1;
        cards.insertOneOff(title, notes, dueOn, position);
    }
}
