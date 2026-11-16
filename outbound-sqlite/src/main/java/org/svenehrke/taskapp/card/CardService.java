package org.svenehrke.taskapp.card;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.svenehrke.taskapp.sqlite.Db;

/**
 * The {@code card} table. Reads join an Occurrence's Recurring Task, so it shows that task's title and notes live
 * (an Occurrence stores none of its own).
 */
@ApplicationScoped
class CardService implements CardRepositorySPI {

    private static final String SELECT = "SELECT c.id, c.recurring_task_id,"
        + " coalesce(c.title, r.title) AS title, coalesce(c.notes, r.notes) AS notes,"
        + " c.due_on, c.original_due_on, c.position, c.done_on"
        + " FROM card c LEFT JOIN recurring_task r ON r.id = c.recurring_task_id";

    @Inject
    Db db;

    @Override
    public Optional<Card> find(long id) {
        return db.queryOne(SELECT + " WHERE c.id = ?", CardService::card, id);
    }

    @Override
    public void setDoneOn(long id, LocalDate doneOn) {
        db.update("UPDATE card SET done_on = ? WHERE id = ?", doneOn, id);
    }

    @Override
    public List<Card> dueOn(LocalDate day) {
        return db.query(SELECT + " WHERE c.due_on = ? ORDER BY c.position, c.id", CardService::card, day);
    }

    @Override
    public void setDueOnAndPosition(long id, LocalDate dueOn, int position) {
        db.update("UPDATE card SET due_on = ?, position = ? WHERE id = ?", dueOn, position, id);
    }

    @Override
    public void setTitleAndNotes(long id, String title, String notes) {
        db.update("UPDATE card SET title = ?, notes = ? WHERE id = ?", title, notes, id);
    }

    @Override
    public OptionalInt maxPosition(LocalDate dueOn) {
        return db.queryOne("SELECT max(position) FROM card WHERE due_on = ?", rs -> {
                var max = rs.getInt(1);
                return rs.wasNull() ? OptionalInt.empty() : OptionalInt.of(max);
            }, dueOn)
            .orElseThrow();
    }

    @Override
    public void insertOneOff(String title, String notes, LocalDate dueOn, int position) {
        db.update("INSERT INTO card (title, notes, due_on, original_due_on, position) VALUES (?, ?, ?, ?, ?)",
            title, notes, dueOn, dueOn, position);
    }

    @Override
    public List<Card> dueBetween(LocalDate from, LocalDate through) {
        return db.query(SELECT + " WHERE c.due_on BETWEEN ? AND ? ORDER BY c.due_on, c.position, c.id",
            CardService::card, from, through);
    }

    @Override
    public List<Card> overdueBox(LocalDate today) {
        return db.query(SELECT + " WHERE c.due_on < ? AND (c.done_on IS NULL OR c.done_on >= ?)"
            + " ORDER BY c.due_on, c.position, c.id", CardService::card, today, today);
    }

    @Override
    public List<Card> openDueFrom(LocalDate from) {
        return db.query(SELECT + " WHERE c.done_on IS NULL AND c.due_on >= ? ORDER BY c.due_on, c.position, c.id",
            CardService::card, from);
    }

    private static Card card(ResultSet rs) throws SQLException {
        return new Card(
            rs.getLong("id"),
            Db.longOrNull(rs, "recurring_task_id"),
            rs.getString("title"),
            rs.getString("notes"),
            Db.date(rs, "due_on"),
            Db.date(rs, "original_due_on"),
            rs.getInt("position"),
            Db.date(rs, "done_on"));
    }
}
