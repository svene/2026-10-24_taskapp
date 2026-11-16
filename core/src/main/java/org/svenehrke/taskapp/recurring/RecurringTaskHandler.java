package org.svenehrke.taskapp.recurring;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.svenehrke.taskapp.shared.RejectedException;

/** Inbound port: the use cases on Recurring Tasks. Commands return {@code void}; the Receiver then reads the Board. */
@ApplicationScoped
public class RecurringTaskHandler {

    private final RecurringTaskRepositorySPI tasks;

    @Inject
    RecurringTaskHandler(RecurringTaskRepositorySPI tasks) {
        this.tasks = tasks;
    }

    /**
     * A new Recurring Task; {@code notes} may be null. A calendar task's {@code produced_through} starts the day
     * before the Start date, so the next catch-up (the Board read that follows) produces from the Start date on,
     * backfilling a past one. An After-done task gets its first Occurrence on the Start date right away.
     */
    @Transactional
    public void create(String title, String notes, Schedule schedule) {
        switch (schedule) {
            case AfterDone afterDone -> tasks.insertOccurrence(tasks.insert(title, notes, afterDone, null), afterDone.startsOn());
            case Weekly w -> tasks.insert(title, notes, w, w.startsOn().minusDays(1));
            case MonthlyOnDay m -> tasks.insert(title, notes, m, m.startsOn().minusDays(1));
            case MonthlyLastDay m -> tasks.insert(title, notes, m, m.startsOn().minusDays(1));
        }
    }

    /**
     * The Recurring Task an Occurrence's edit modal shows.
     *
     * @throws RejectedException for an unknown id
     */
    @Transactional
    public RecurringTask recurringTask(long id) {
        return tasks.find(id).orElseThrow(() -> new RejectedException("That recurring task does not exist (any more)."));
    }
}
