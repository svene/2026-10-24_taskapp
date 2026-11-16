package org.svenehrke.taskapp.recurring;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.svenehrke.taskapp.sqlite.Db;

/** The {@code recurring_task} table, and the Occurrence inserts into {@code card}. */
@ApplicationScoped
class RecurringTaskService implements RecurringTaskRepositorySPI {

    private static final String COLUMNS =
        "id, title, notes, starts_on, stopped_on, kind, every_weeks, weekdays, produced_through";

    @Inject
    Db db;

    @Override
    public void insert(String title, String notes, Schedule schedule, LocalDate producedThrough) {
        switch (schedule) {
            case Weekly weekly -> db.update("INSERT INTO recurring_task"
                    + " (title, notes, starts_on, kind, every_weeks, weekdays, produced_through)"
                    + " VALUES (?, ?, ?, 'WEEKLY', ?, ?, ?)",
                title, notes, weekly.startsOn(), weekly.everyWeeks(), mask(weekly.weekdays()), producedThrough);
        }
    }

    @Override
    public Optional<RecurringTask> find(long id) {
        return db.queryOne("SELECT " + COLUMNS + " FROM recurring_task WHERE id = ?", RecurringTaskService::task, id);
    }

    @Override
    public List<RecurringTask> producingOnCalendar() {
        return db.query("SELECT " + COLUMNS + " FROM recurring_task"
            + " WHERE stopped_on IS NULL AND kind <> 'AFTER_DONE' ORDER BY id", RecurringTaskService::task);
    }

    /** {@code WHERE true} keeps SQLite from reading {@code ON CONFLICT} as part of the {@code SELECT}. */
    @Override
    public void insertOccurrence(long recurringTaskId, LocalDate dueOn) {
        db.update("INSERT INTO card (recurring_task_id, due_on, original_due_on, position)"
                + " SELECT ?, ?, ?, coalesce((SELECT max(position) + 1 FROM card WHERE due_on = ?), 0) WHERE true"
                + " ON CONFLICT DO NOTHING",
            recurringTaskId, dueOn, dueOn, dueOn);
    }

    @Override
    public void setProducedThrough(long id, LocalDate producedThrough) {
        db.update("UPDATE recurring_task SET produced_through = ? WHERE id = ?", producedThrough, id);
    }

    private static RecurringTask task(ResultSet rs) throws SQLException {
        var kind = rs.getString("kind");
        Schedule schedule = switch (kind) {
            case "WEEKLY" -> new Weekly(weekdays(rs.getInt("weekdays")), rs.getInt("every_weeks"),
                Db.date(rs, "starts_on"));
            default -> throw new IllegalStateException("Schedule kind not supported yet: " + kind);
        };
        return new RecurringTask(rs.getLong("id"), rs.getString("title"), rs.getString("notes"), schedule,
            Db.date(rs, "produced_through"), Db.date(rs, "stopped_on"));
    }

    /** Monday is bit 0 (1), Sunday bit 6 (64). */
    static int mask(Set<DayOfWeek> weekdays) {
        return weekdays.stream().mapToInt(day -> 1 << (day.getValue() - 1)).reduce(0, (a, b) -> a | b);
    }

    static Set<DayOfWeek> weekdays(int mask) {
        var days = EnumSet.noneOf(DayOfWeek.class);
        for (var day : DayOfWeek.values()) {
            if ((mask & (1 << (day.getValue() - 1))) != 0) days.add(day);
        }
        return days;
    }
}
