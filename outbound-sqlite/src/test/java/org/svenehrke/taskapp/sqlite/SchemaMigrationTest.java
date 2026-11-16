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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2026-10-5", "25.10.2026", "tomorrow"})
    void rejects_a_malformed_date(String date) throws SQLException {
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.prepareStatement("""
                 insert into card (title, due_on, original_due_on, position)
                 values ('Water the plants', ?, '2026-10-25', 0)""")) {
            statement.setString(1, date);
            assertThatThrownBy(statement::executeUpdate)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("CHECK constraint failed");
        }
    }

    @Test
    void accepts_a_null_optional_date() throws SQLException {
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                insert into card (title, due_on, original_due_on, position, done_on)
                values ('Water the plants', '2026-10-25', '2026-10-25', 0, null)""");
        }
    }
}
