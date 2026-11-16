package org.svenehrke.taskapp.board;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDate;

/** Inbound port: the Board as of today. */
@ApplicationScoped
public class BoardHandler {

    private final Clock clock;

    @Inject
    BoardHandler(Clock clock) {
        this.clock = clock;
    }

    @Transactional
    public Board board() {
        return Board.startingOn(LocalDate.now(clock));
    }
}
