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
        "id, title, notes, starts_on, stopped_on, kind, every_weeks, weekdays, month_day, after_amount, after_unit,"
            + " produced_through";

    @Inject
    Db db;

    @Override
    public long insert(String title, String notes, Schedule schedule, LocalDate producedThrough) {
        return switch (schedule) {
            case Weekly weekly -> insertReturningId(
                "(title, notes, starts_on, kind, every_weeks, weekdays, produced_through) VALUES (?, ?, ?, 'WEEKLY', ?, ?, ?)",
                title, notes, weekly.startsOn(), weekly.everyWeeks(), mask(weekly.weekdays()), producedThrough);
            case MonthlyOnDay monthly -> insertReturningId(
                "(title, notes, starts_on, kind, month_day, produced_through) VALUES (?, ?, ?, 'MONTHLY_DAY', ?, ?)",
                title, notes, monthly.startsOn(), monthly.day(), producedThrough);
            case MonthlyLastDay monthly -> insertReturningId(
                "(title, notes, starts_on, kind, produced_through) VALUES (?, ?, ?, 'MONTHLY_LAST', ?)",
                title, notes, monthly.startsOn(), producedThrough);
            case AfterDone afterDone -> insertReturningId(
                "(title, notes, starts_on, kind, after_amount, after_unit) VALUES (?, ?, ?, 'AFTER_DONE', ?, ?)",
                title, notes, afterDone.startsOn(), afterDone.amount(), afterDone.unit().name());
        };
    }

    private long insertReturningId(String columnsAndValues, Object... params) {
        return db.queryOne("INSERT INTO recurring_task " + columnsAndValues + " RETURNING id", rs -> rs.getLong(1), params)
            .orElseThrow();
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
    public Optional<LocalDate> latestOriginalDueOn(long recurringTaskId) {
        return db.queryOne("SELECT original_due_on FROM card WHERE recurring_task_id = ?"
            + " ORDER BY original_due_on DESC LIMIT 1", rs -> Db.date(rs, "original_due_on"), recurringTaskId);
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
            case "MONTHLY_DAY" -> new MonthlyOnDay(rs.getInt("month_day"), Db.date(rs, "starts_on"));
            case "MONTHLY_LAST" -> new MonthlyLastDay(Db.date(rs, "starts_on"));
            case "AFTER_DONE" -> new AfterDone(rs.getInt("after_amount"), AfterDone.Unit.valueOf(rs.getString("after_unit")),
                Db.date(rs, "starts_on"));
            default -> throw new IllegalStateException("Unknown Schedule kind: " + kind);
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
