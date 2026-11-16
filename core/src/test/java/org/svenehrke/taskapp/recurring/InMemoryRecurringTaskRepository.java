package org.svenehrke.taskapp.recurring;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** {@link RecurringTaskRepositorySPI} over lists, for unit tests of core. Occurrences are kept as (task, date) only. */
public class InMemoryRecurringTaskRepository implements RecurringTaskRepositorySPI {

    public record Occurrence(long recurringTaskId, LocalDate originalDueOn) {}

    private final List<RecurringTask> rows = new ArrayList<>();
    private final List<Occurrence> occurrences = new ArrayList<>();

    /** In insertion order. */
    public List<Occurrence> occurrences() {
        return List.copyOf(occurrences);
    }

    public void stop(long id, LocalDate stoppedOn) {
        rows.replaceAll(t -> t.id() != id ? t
            : new RecurringTask(t.id(), t.title(), t.notes(), t.schedule(), t.producedThrough(), stoppedOn));
    }

    @Override
    public long insert(String title, String notes, Schedule schedule, LocalDate producedThrough) {
        long id = rows.size() + 1;
        rows.add(new RecurringTask(id, title, notes, schedule, producedThrough, null));
        return id;
    }

    @Override
    public Optional<RecurringTask> find(long id) {
        return rows.stream().filter(t -> t.id() == id).findFirst();
    }

    @Override
    public List<RecurringTask> producingOnCalendar() {
        return rows.stream().filter(t -> t.stoppedOn() == null && t.schedule() instanceof CalendarSchedule).toList();
    }

    @Override
    public void insertOccurrence(long recurringTaskId, LocalDate dueOn) {
        var occurrence = new Occurrence(recurringTaskId, dueOn);
        if (!occurrences.contains(occurrence)) occurrences.add(occurrence);
    }

    @Override
    public Optional<LocalDate> latestOriginalDueOn(long recurringTaskId) {
        return occurrences.stream().filter(o -> o.recurringTaskId() == recurringTaskId)
            .map(Occurrence::originalDueOn).max(LocalDate::compareTo);
    }

    @Override
    public void setProducedThrough(long id, LocalDate producedThrough) {
        rows.replaceAll(t -> t.id() != id ? t
            : new RecurringTask(t.id(), t.title(), t.notes(), t.schedule(), producedThrough, t.stoppedOn()));
    }
}
