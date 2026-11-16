package org.svenehrke.taskapp.board;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Predicate;
import org.svenehrke.taskapp.card.Card;
import org.svenehrke.taskapp.card.CardRepositorySPI;
import org.svenehrke.taskapp.recurring.OccurrenceProducer;
import org.svenehrke.taskapp.recurring.RecurringTask;
import org.svenehrke.taskapp.recurring.RecurringTaskRepositorySPI;

/** Inbound port: the Board as of today. */
@ApplicationScoped
public class BoardHandler {

    private final Clock clock;
    private final CardRepositorySPI cards;
    private final OccurrenceProducer occurrences;
    private final RecurringTaskRepositorySPI recurringTasks;

    @Inject
    BoardHandler(Clock clock, CardRepositorySPI cards, OccurrenceProducer occurrences, RecurringTaskRepositorySPI recurringTasks) {
        this.clock = clock;
        this.cards = cards;
        this.occurrences = occurrences;
        this.recurringTasks = recurringTasks;
    }

    /** Catches up the Recurring Tasks' Occurrences first (spec §2.3), so the Board read sees them. */
    @Transactional
    public Board board() {
        var today = LocalDate.now(clock);
        occurrences.catchUp(today);
        var futureFrom = Board.futureFrom(today);
        return Board.of(today, cards.overdueBox(today), cards.dueBetween(today, futureFrom.minusDays(1)), cards.openDueFrom(futureFrom), stale(today));
    }

    /** Looks each Recurring Task up once per Board read. */
    private Predicate<Card> stale(LocalDate today) {
        var tasks = new HashMap<Long, Optional<RecurringTask>>();
        return card -> card.recurringTaskId() != null
            && tasks.computeIfAbsent(card.recurringTaskId(), recurringTasks::find)
                .map(task -> task.stale(card.originalDueOn(), card.dueOn(), card.done(), today))
                .orElse(false);
    }
}
