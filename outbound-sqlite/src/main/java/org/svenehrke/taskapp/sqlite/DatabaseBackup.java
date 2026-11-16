package org.svenehrke.taskapp.sqlite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

/**
 * The startup backup (spec §6): a consistent copy of the SQLite file via {@code VACUUM INTO}, into
 * {@code backups/taskapp-<timestamp>.db} next to it, keeping the newest {@value #KEEP}. The timestamp sorts by name,
 * so the newest backups are the last names.
 */
final class DatabaseBackup {

    static final int KEEP = 7;

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS");

    private DatabaseBackup() {
    }

    /** Backs up {@code dbFile} as of {@code now} and rotates; does nothing while the file does not exist yet. */
    static void backUp(Path dbFile, LocalDateTime now) {
        if (!Files.exists(dbFile)) return;
        var dir = backupDir(dbFile);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create the backup directory " + dir, e);
        }
        var backup = dir.resolve("taskapp-" + TIMESTAMP.format(now) + ".db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             var statement = connection.prepareStatement("vacuum into ?")) {
            statement.setString(1, backup.toString());
            statement.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("cannot back up the database to " + backup, e);
        }
        rotate(dir);
    }

    static Path backupDir(Path dbFile) {
        return dbFile.toAbsolutePath().getParent().resolve("backups");
    }

    private static void rotate(Path dir) {
        try (var backups = Files.list(dir)) {
            var stale = backups
                .filter(f -> f.getFileName().toString().matches("taskapp-.*\\.db"))
                .sorted(Comparator.comparing(Path::getFileName).reversed())
                .skip(KEEP)
                .toList();
            for (var f : stale) Files.delete(f);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot rotate the backups in " + dir, e);
        }
    }
}
