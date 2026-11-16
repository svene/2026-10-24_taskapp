package org.svenehrke.taskapp.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseBackupTest {

    private static final LocalDateTime SUN_25_OCT = LocalDateTime.of(2026, 10, 25, 9, 30, 15, 123_000_000);

    @TempDir
    Path dir;

    @Test
    void copies_the_database_into_backups_next_to_it() throws SQLException, IOException {
        var db = dir.resolve("taskapp.db");
        execute(db, "create table card (title text)", "insert into card values ('Water the plants')");

        DatabaseBackup.backUp(db, SUN_25_OCT);

        assertThat(backups()).containsExactly("taskapp-20261025-093015-123.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("backups/taskapp-20261025-093015-123.db"));
             var result = connection.createStatement().executeQuery("select title from card")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("Water the plants");
        }
    }

    @Test
    void keeps_the_newest_seven() throws SQLException, IOException {
        var db = dir.resolve("taskapp.db");
        execute(db, "create table card (title text)");

        for (int day = 1; day <= 9; day++) DatabaseBackup.backUp(db, SUN_25_OCT.withDayOfMonth(day));

        assertThat(backups()).containsExactly(
            "taskapp-20261003-093015-123.db", "taskapp-20261004-093015-123.db", "taskapp-20261005-093015-123.db",
            "taskapp-20261006-093015-123.db", "taskapp-20261007-093015-123.db", "taskapp-20261008-093015-123.db",
            "taskapp-20261009-093015-123.db");
    }

    @Test
    void rotation_leaves_other_files_alone() throws SQLException, IOException {
        var db = dir.resolve("taskapp.db");
        execute(db, "create table card (title text)");
        Files.createDirectories(dir.resolve("backups"));
        Files.writeString(dir.resolve("backups/keep-me.txt"), "mine");

        for (int day = 1; day <= 8; day++) DatabaseBackup.backUp(db, SUN_25_OCT.withDayOfMonth(day));

        assertThat(backups()).hasSize(8).contains("keep-me.txt").doesNotContain("taskapp-20261001-093015-123.db");
    }

    @Test
    void no_backup_before_the_database_exists() {
        DatabaseBackup.backUp(dir.resolve("taskapp.db"), SUN_25_OCT);

        assertThat(dir.resolve("backups")).doesNotExist();
        assertThat(dir.resolve("taskapp.db")).doesNotExist();
    }

    private List<String> backups() throws IOException {
        try (var files = Files.list(dir.resolve("backups"))) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static void execute(Path db, String... sqls) throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + db); var statement = connection.createStatement()) {
            for (var sql : sqls) statement.execute(sql);
        }
    }
}
