package org.svenehrke.taskapp.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** V1 on a fresh SQLite file, without Quarkus: the migration applies and its check constraints bite. */
class SchemaMigrationTest {

    @TempDir
    Path dir;

    String url;

    @BeforeEach
    void migrate() {
        url = "jdbc:sqlite:" + dir.resolve("taskapp.db");
        var result = Flyway.configure().dataSource(url, null, null).load().migrate();
        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("1");
    }

    @Test
    void accepts_a_well_formed_card() throws SQLException {
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                insert into card (title, due_on, original_due_on, position)
                values ('Water the plants', '2026-10-25', '2026-10-25', 0)""");
        }
    }

    /**
     * {@code check (x = date(x))} catches a date SQLite can parse but normalizes differently. Text it cannot parse
     * at all (e.g. {@code 25.10.2026}) gets past it: {@code date(x)} is null there, and a null check passes.
     */
    @Test
    void rejects_an_impossible_date() throws SQLException {
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("""
                insert into card (title, due_on, original_due_on, position)
                values ('Water the plants', '2026-02-30', '2026-02-30', 0)"""))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("CHECK constraint failed");
        }
    }
}
