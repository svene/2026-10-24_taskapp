package org.svenehrke.taskapp.card;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import org.svenehrke.taskapp.sqlite.Db;

/** The {@code card} table. */
@ApplicationScoped
class CardService implements CardRepositorySPI {

    private static final String COLUMNS =
        "id, recurring_task_id, title, notes, due_on, original_due_on, position, done_on";

    @Inject
    Db db;

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
        return db.query("SELECT " + COLUMNS + " FROM card WHERE due_on BETWEEN ? AND ? ORDER BY due_on, position, id",
            CardService::card, from, through);
    }

    @Override
    public List<Card> openDueFrom(LocalDate from) {
        return db.query("SELECT " + COLUMNS + " FROM card WHERE done_on IS NULL AND due_on >= ? ORDER BY due_on, position, id",
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
