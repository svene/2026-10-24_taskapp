package org.svenehrke.taskapp.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Points {@code taskapp.db.path} at a fresh temp file for this test JVM, so a test run never touches the dev or the
 * real database. Registered in {@code META-INF/services}; its ordinal beats application.properties.
 */
public class TempDatabaseConfigSource implements ConfigSource {

    private static final String DB_PATH = createTempDbPath();

    @Override
    public Map<String, String> getProperties() {
        return Map.of("taskapp.db.path", DB_PATH);
    }

    @Override
    public Set<String> getPropertyNames() {
        return getProperties().keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return getProperties().get(propertyName);
    }

    @Override
    public String getName() {
        return "taskapp-temp-database";
    }

    @Override
    public int getOrdinal() {
        return 500;
    }

    private static String createTempDbPath() {
        try {
            var dir = Files.createTempDirectory("taskapp-test-");
            return dir.resolve("taskapp.db").toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
