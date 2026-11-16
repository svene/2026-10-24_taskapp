package org.svenehrke.taskapp.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/** The datasource as the app gets it: migrated on startup, with the per-connection PRAGMAs applied. */
@QuarkusTest
class DatabaseTest {

    @Inject
    AgroalDataSource dataSource;

    @Test
    void schema_is_migrated_to_v1_on_startup() throws SQLException {
        assertThat(queryString("select max(version) from flyway_schema_history where success = 1")).isEqualTo("1");
        // the table exists; other tests may have added rows to the shared test database
        assertThat(queryString("select count(*) from card where id < 0")).isEqualTo("0");
    }

    @Test
    void connection_has_foreign_keys_wal_and_busy_timeout() throws SQLException {
        assertThat(queryString("pragma foreign_keys")).isEqualTo("1");
        assertThat(queryString("pragma journal_mode")).isEqualTo("wal");
        assertThat(queryString("pragma busy_timeout")).isEqualTo("5000");
    }

    private String queryString(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
