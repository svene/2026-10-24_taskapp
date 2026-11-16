# Taskapp

A personal, calendar-style task board for one user, running locally on a laptop. Cards sit on the days they are due
across a rolling two-week span; One-off Tasks and Recurring Tasks (weekly, monthly or "after done") live side by side.
Data is a single SQLite file.

## Install and run

Needs Java 25 and Maven on `PATH`, and `~/.local/bin` on `PATH`.

```sh
./install.sh   # builds the uber-jar; installs ~/.local/share/taskapp/taskapp.jar and ~/.local/bin/taskapp
taskapp        # foreground, logs to the console; Ctrl-C stops it
```

Then open <http://127.0.0.1:7070>. To upgrade, pull and run `./install.sh` again.

| What | Where | Override |
|------|-------|----------|
| Port | `127.0.0.1:7070` (loopback only) | `QUARKUS_HTTP_PORT` |
| Database | `~/.local/share/taskapp/taskapp.db` | `TASKAPP_DB_PATH` |
| Backups | `backups/` next to the database; one per startup, newest 7 kept | follows the database |

To restore a backup, stop the app and copy a `backups/taskapp-<timestamp>.db` over `taskapp.db`
(delete `taskapp.db-wal` and `taskapp.db-shm` first).

## Develop

```sh
mvn quarkus:dev -pl app-server   # http://localhost:8080, DB in app-server/.data/taskapp/dev.db
mvn verify                       # unit, @QuarkusTest and ArchUnit tests
```

Modules (hexagonal): `core` (domain and use cases), `inbound-http-html` (HTTP receivers and browser templating),
`outbound-sqlite` (persistence, migrations, startup backup), `app-server` (wiring, configuration, the runnable jar).

## Docs

- [`docs/spec.md`](docs/spec.md): what the app does
- [`GLOSSARY.md`](GLOSSARY.md): the domain vocabulary
- [`concepts.md`](concepts.md) and [`docs/architecture/`](docs/architecture/README.md): how it is built, and why
- [`docs/adr/`](docs/adr/): architecture decisions
