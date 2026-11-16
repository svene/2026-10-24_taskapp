package org.svenehrke.taskapp.sqlite;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.flywaydb.core.Flyway;

/**
 * Prepares the SQLite file on startup, before anything else touches it: creates its parent directory (SQLite
 * creates the file, not the directories) and then runs the Flyway migrations. That is why
 * {@code quarkus.flyway.migrate-at-start} is off: Quarkus would migrate before this observer could run.
 */
@ApplicationScoped
class DatabaseStartup {

    @ConfigProperty(name = "taskapp.db.path")
    String dbPath;

    @Inject
    Flyway flyway;

    void onStart(@Observes StartupEvent event) {
        createParentDirectory(Path.of(dbPath));
        flyway.migrate();
    }

    private static void createParentDirectory(Path dbFile) {
        var parent = dbFile.toAbsolutePath().getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create the database directory " + parent, e);
        }
    }
}
