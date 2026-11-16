package org.svenehrke.taskapp.recurring;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import org.svenehrke.taskapp.shared.RejectedException;

/** Inbound port: the use cases on Recurring Tasks. Commands return {@code void}; the Receiver then reads the Board. */
@ApplicationScoped
public class RecurringTaskHandler {

    private final Clock clock;
    private final RecurringTaskRepositorySPI tasks;
    private final OccurrenceProducer producer;

    @Inject
    RecurringTaskHandler(Clock clock, RecurringTaskRepositorySPI tasks, OccurrenceProducer producer) {
        this.clock = clock;
        this.tasks = tasks;
        this.producer = producer;
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

    /**
     * Edits title, notes and Schedule (spec §2.4). The title and notes reach every Occurrence that has not
     * overridden them. A calendar Schedule produces anew from today on: Occurrences it produces again are adopted,
     * the others stay and are Stale; nothing before today is backfilled. An After-done Schedule takes effect at the
     * next tick-off. Switching between calendar and After-done is not an edit.
     *
     * @throws RejectedException for an unknown or Stopped task, or a switch between calendar and After-done
     */
    @Transactional
    public void edit(long id, String title, String notes, Schedule schedule) {
        var task = producing(id);
        if ((task.schedule() instanceof AfterDone) != (schedule instanceof AfterDone)) {
            throw new RejectedException("A recurring task cannot switch between calendar and after-done; stop it and create a new one.");
        }
        var today = LocalDate.now(clock);
        switch (schedule) {
            case AfterDone afterDone -> tasks.update(id, title, notes, afterDone, null);
            case CalendarSchedule calendar -> {
                tasks.update(id, title, notes, schedule, today.minusDays(1));
                producer.reproduce(id, calendar, today);
            }
        }
    }

    /**
     * Stops the task for good (spec §2.4): it produces no more Occurrences; its open future ones are Stale.
     *
     * @throws RejectedException for an unknown or already Stopped task
     */
    @Transactional
    public void stop(long id) {
        producing(id);
        tasks.stop(id, LocalDate.now(clock));
    }

    private RecurringTask producing(long id) {
        var task = recurringTask(id);
        if (task.stopped()) throw new RejectedException("That recurring task is stopped.");
        return task;
    }
}
