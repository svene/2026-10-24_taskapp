package org.svenehrke.taskapp.sqlite;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Plain SQL over JDBC, without an ORM: every read maps its rows by hand, every write is an explicit statement.
 * <p>
 * Each call takes a connection from the pool and gives it back. Inside a {@code @Transactional} method Agroal hands
 * out the transaction's connection every time, so all statements of the method commit (or roll back) together.
 * <p>
 * Dates are ISO text in SQLite: a {@link LocalDate} parameter is bound as {@code 2026-10-25}, and
 * {@link #date(ResultSet, String)} reads one back.
 */
@ApplicationScoped
public class Db {

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    @Inject
    AgroalDataSource dataSource;

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (var rs = statement.executeQuery()) {
                var rows = new ArrayList<T>();
                while (rs.next()) rows.add(mapper.map(rs));
                return rows;
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(sql, e);
        }
    }

    /** The first row, if there is one. */
    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        return query(sql, mapper, params).stream().findFirst();
    }

    /** {@code INSERT}, {@code UPDATE} or {@code DELETE}; returns the number of rows changed. */
    public int update(String sql, Object... params) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new UncheckedSqlException(sql, e);
        }
    }

    public static LocalDate date(ResultSet rs, String column) throws SQLException {
        var value = rs.getString(column);
        return value == null ? null : LocalDate.parse(value);
    }

    /** A nullable integer column. */
    public static Long longOrNull(ResultSet rs, String column) throws SQLException {
        var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, switch (params[i]) {
                case LocalDate date -> date.toString();
                case null, default -> params[i];
            });
        }
    }

    public static class UncheckedSqlException extends RuntimeException {
        UncheckedSqlException(String sql, SQLException cause) {
            super(cause.getMessage() + " [" + sql + "]", cause);
        }
    }
}
