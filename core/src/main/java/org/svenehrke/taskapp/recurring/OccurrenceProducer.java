package org.svenehrke.taskapp.recurring;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
import org.svenehrke.taskapp.shared.Span;

/**
 * Produces Occurrences (spec §2.3). Not a Handler: it runs inside the transaction of the Handler that calls it.
 */
@ApplicationScoped
public class OccurrenceProducer {

    private final RecurringTaskRepositorySPI tasks;

    @Inject
    public OccurrenceProducer(RecurringTaskRepositorySPI tasks) {
        this.tasks = tasks;
    }

    /**
     * Lazy catch-up, before every read of the Board: each calendar Recurring Task that is not Stopped gets every date
     * after its {@code produced_through} up to the end of the span, plus the first date beyond the span, and the mark
     * advances to that date. Missed dates (laptop off, a past Start date) become Overdue Occurrences. Running it again
     * the same day creates nothing.
     */
    public void catchUp(LocalDate today) {
        var beyondSpan = Span.futureFrom(today);
        for (var task : tasks.producingOnCalendar()) {
            if (!(task.schedule() instanceof CalendarSchedule calendar)) continue;
            var last = calendar.nextOnOrAfter(beyondSpan);
            var from = task.producedThrough().plusDays(1);
            if (from.isAfter(last)) continue;
            calendar.datesFrom(from).takeWhile(date -> !date.isAfter(last))
                .forEach(date -> tasks.insertOccurrence(task.id(), date));
            tasks.setProducedThrough(task.id(), last);
        }
    }

    /**
     * The tick hook of spec §2.3: ticking off an Occurrence of an After-done task that is not Stopped creates its
     * successor, due {@link AfterDone#next} after {@code doneOn}, but only when the ticked one is the latest (no
     * Occurrence of the task has a later original due date). Unticking and ticking again therefore creates nothing,
     * and the unique original due date keeps a repeated call harmless. Anything else is left alone.
     */
    public void afterDone(long recurringTaskId, LocalDate originalDueOn, LocalDate dueOn, LocalDate doneOn) {
        var task = tasks.find(recurringTaskId).orElse(null);
        if (task == null || task.stoppedOn() != null || !(task.schedule() instanceof AfterDone afterDone)) return;
        if (tasks.latestOriginalDueOn(recurringTaskId).filter(latest -> latest.isAfter(originalDueOn)).isPresent()) return;
        tasks.insertOccurrence(recurringTaskId, afterDone.next(originalDueOn, dueOn, doneOn));
    }
}
