package org.svenehrke.taskapp.board;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import org.svenehrke.taskapp.card.CardRepositorySPI;

/** Inbound port: the Board as of today. */
@ApplicationScoped
public class BoardHandler {

    private final Clock clock;
    private final CardRepositorySPI cards;

    @Inject
    BoardHandler(Clock clock, CardRepositorySPI cards) {
        this.clock = clock;
        this.cards = cards;
    }

    @Transactional
    public Board board() {
        var today = LocalDate.now(clock);
        var futureFrom = Board.futureFrom(today);
        return Board.of(today, cards.dueBetween(today, futureFrom.minusDays(1)), cards.openDueFrom(futureFrom));
    }
}
