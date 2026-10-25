# SQLite instead of Postgres

The reference architecture uses Postgres (Dev Services in dev, a container via docker compose when built). This app is single-user and runs only on the owner's laptop, so it stores its data in one SQLite file instead (`~/.local/share/taskapp/taskapp.db`, configurable). That means no container or compose setup, and a backup is a file copy. The outbound adapter is `outbound-sqlite` (plain JDBC + Flyway via `quarkus-jdbc-sqlite`), not `outbound-postgres`.

## Consequences

- No date type: dates are ISO `YYYY-MM-DD` text with `check (x = date(x))`; the adapter binds them as strings. The xerial driver otherwise writes `java.sql.Date`/`LocalDate` as epoch milliseconds.
- No array type: a Weekly Schedule's weekdays are a 7-bit integer bitmask (Mon = 1 … Sun = 64).
- Every table is `STRICT`, so the loose typing can't creep in.
- Every connection sets `PRAGMA foreign_keys = ON` (off by default), WAL mode and a `busy_timeout`; the Agroal pool has size 1, because SQLite allows one writer at a time.
- No Dev Services: tests run against a fresh temp-file database.
- `ALTER TABLE` is limited: a later migration that changes a constraint rebuilds the table (create, copy, drop, rename).
