# Taskapp v1 spec

The build-ready spec for Taskapp v1: a single-user, locally run task app with a calendar-style Board. Each section records what was decided, *as it finally stands*. The reasoning lives in the deciding tickets of the planning map, [Taskapp — build-ready spec](https://github.com/svene/2026-10-24_taskapp/issues/1), linked from each section. Vocabulary follows [`GLOSSARY.md`](../GLOSSARY.md). Once implementation starts, this file is what gets changed. The tickets stay as history.

## 1. Scope

- One user, no authentication. Runs only on the owner's laptop.
- Out of scope for v1:
  - tags, priority, categories, attachments
  - a history/archive view
  - deleting tasks (tick them off instead)
  - pausing a Recurring Task
  - per-Occurrence title/notes
  - a configurable span length
  - SSE
  - a JSON API
  - Playwright tests
  - yearly, Nth-weekday and calendar "every N days" Schedules, and months as an After-done unit
  - switching a Recurring Task between a calendar and an After-done Schedule

## 2. Domain

Decided in:
- [Recurring Task lifecycle](https://github.com/svene/2026-10-24_taskapp/issues/2)
- [Board as a rolling calendar](https://github.com/svene/2026-10-24_taskapp/issues/8)
- [Schedule representation and persistence](https://github.com/svene/2026-10-24_taskapp/issues/6)

### 2.1 Cards

- A **Card** is a One-off Task or an Occurrence. A Recurring Task is never on the Board itself; its Schedule produces Occurrences.
- Every Card has a mandatory **due date** (date-only, default today) and an **original due date**:
  - for a One-off Task, the date it was created with;
  - for an Occurrence, the date its Schedule produced.
- A Card is open or **Done**. Ticking off stores the done date (`done_on`, which may precede the due date). Unticking makes the Card open again and undoes nothing else. There is no Doing and no delete.
- **Overdue** = the due date is before today and the Card is not Done.
- One-off Task fields: title (non-blank), optional notes, due date.
- An Occurrence has no title or notes of its own. It shows its Recurring Task's title and notes live, so renaming the Recurring Task renames every Occurrence, Done ones included.
- Several open Occurrences of one Recurring Task may exist at once, even on the same day.

### 2.2 Schedules

In `core`: `sealed interface Schedule permits Weekly, MonthlyOnDay, MonthlyLastDay, AfterDone`.

| Kind | Parameters | Produces |
|---|---|---|
| `Weekly` | weekdays (set of ISO 1–7), every N ≥ 1 weeks | the chosen weekdays, every Nth ISO week, counted from the week containing the Start date |
| `MonthlyOnDay` | day 1–28 | that day of every month |
| `MonthlyLastDay` | – | the last day of every month |
| `AfterDone` | N ≥ 1, unit `DAYS` / `WEEKS` | first Occurrence on the Start date; each next one on the previous Occurrence's done date + N |

- Every Schedule has a **Start date** (default today, editable). Nothing is produced before it.
- Calendar kinds answer `produces(LocalDate)` and "next dates on or after a day".
- Dates are `LocalDate` in the laptop's default zone. "Today" comes from an injected `Clock`.
- Only the current Schedule is stored. An edit overwrites it.

### 2.3 Producing Occurrences

Decided in [When due Occurrences are created](https://github.com/svene/2026-10-24_taskapp/issues/7).

**Lazy catch-up.** Every request that returns the Board first runs `catchUp(today)` in the same transaction. There is no scheduler and no startup hook. A GET that writes is accepted.

**Calendar kinds:**
- Each one keeps `produced_through`, the latest original due date it has produced.
- Catch-up creates every date after `produced_through`, up to the end of the span, plus the first date beyond the span. Then it advances the mark.
- Stopped Recurring Tasks are skipped.
- Missed dates (laptop off) are backfilled as Overdue Occurrences.
- Creating a Recurring Task with a past Start date backfills from that date: `produced_through` starts at Start date − 1.

**After-done:**
- The first Occurrence is created with the Recurring Task, due on the Start date.
- Ticking off creates the successor only if the ticked Occurrence is the latest one (highest original due date) and the task is not Stopped. So untick/retick never creates a second successor.

**Idempotent inserts:** a unique (recurring task, original due date) constraint plus `ON CONFLICT DO NOTHING`.

New Occurrences go to the bottom of their day.

### 2.4 Editing and stopping a Recurring Task

- **Edit** changes the title, notes and Schedule.
  - Changing between calendar kinds is allowed. Calendar ↔ After-done is not: Stop the task and create a new one.
  - A calendar Schedule edit re-produces from **today** onward and never backfills dates before the edit day.
  - Re-producing **adopts** an existing open Occurrence that has the same *original* due date, instead of creating a second Card.
  - For After-done, a change to N or the unit applies from the next completion.
- **Stop** sets `stopped_on`. No more Occurrences are produced, and the existing ones remain. Editing a Stopped Recurring Task is rejected.
- **Stale Occurrence:** an open Occurrence due after today whose original due date the current Schedule would no longer produce, or whose Recurring Task is Stopped.
  - Stale is computed in `core`, never stored.
  - The Card shows a Stale tag, and the owner closes it by hand.
  - A dragged Occurrence whose original date is no longer produced is Stale, even if it now sits on a produced date.
  - After-done Occurrences only become Stale through Stop.

### 2.5 Move

Decided in [Shape of the move operation](https://github.com/svene/2026-10-24_taskapp/issues/9).

- One command, `Move(card, target, before?)`. Drag-and-drop, the card buttons and the edit modal's due-date field all use it.
- **Target:**
  - a day from today on;
  - or `future`, which the server turns into the day after the span.
  - A past day is rejected.
- **`before`:** a card id. Without it, or when that card is not on the target day, the moved Card goes to the bottom.
- **Position:** each Card has an integer `position` scoped to its due date.
  - On every Move or insert, the target day is renumbered densely (0..n). The source day is not renumbered.
  - New Cards get max + 1.
  - The Board orders a day by `position, id`.
- **What a Move changes:** only the due date and position.
  - The original due date, Stale, `produced_through` and the After-done successor are unaffected.
  - Moving a Card back to its original day clears the ⇢ marker.
- Moving a Done Card is rejected. Ticking off is not a Move, and Done Cards keep their position.

## 3. Board

Decided in:
- [Board as a rolling calendar](https://github.com/svene/2026-10-24_taskapp/issues/8)
- [Board look and behaviour](https://github.com/svene/2026-10-24_taskapp/issues/5), which has a prototype on branch [`prototype/board-look-and-behaviour`](https://github.com/svene/2026-10-24_taskapp/tree/prototype/board-look-and-behaviour/prototypes/board), variant B

### 3.1 Layout

- 14 day Columns rolling from today, plus **Future**. They are laid out as two rows of 7 day Columns with Future as a sidebar spanning both rows. There is no horizontal scroll.
- Day Columns are labelled with weekday and date, and weekends are tinted.
- **Today** has an **Overdue box** at its top: every Overdue Card, sorted by due date (oldest first) and then by position.
  - The box cannot be reordered and accepts no drops.
  - Dragging a Card out of it is an ordinary Move.
  - A Done Overdue Card stays struck through in Today until the end of the day it was ticked off.
- A Done Card stays struck through in its due date's Column, including one ticked off early on a future day. It leaves the Board once that day is past.
- **Future** shows the 5 nearest open Cards due after the span, sorted by date and then position, each with its date, plus "+N more".
  - Stale Occurrences don't take any of the 5 slots; they appear only after "+N more".
  - Future cannot be reordered. A drop on Future means "the day after the span, at the bottom".
- **Compact cards:** title plus markers:
  - ⚠ and the date when Overdue
  - ↻ for an Occurrence
  - ⇢ and the original due date when it differs
  - a Stale tag

  Card actions appear on hover.
- **Theme:** follows the system light/dark setting by default (Bulma 1.x). A top-bar toggle overrides it, stored in `localStorage` as `data-theme` on `<html>`, never in the DB. Custom styles use Bulma CSS variables only.

### 3.2 Interaction

- **Drag-and-drop:**
  - Dragging to a day reschedules the Card.
  - Dragging within a day reorders it.
  - A drop on Future sends the Card to the day after the span.
  - Done Cards don't drag, but open Cards can be dropped above or below them.
- **Card buttons** are the non-drag fallback:
  - →Today (Overdue only), +1 day, +1 week. They post absolute dates, put into the view model by the server, to the Move endpoint.
  - Edit.
- **Tick checkbox:** ticks off or unticks the Card.
- **Creating:**
  - A quick-add field at the bottom of each day Column creates a One-off Task due that day.
  - "+" in a Column header opens the modal with the due date set to that day.
- **Modal kinds:** One-off, *Repeats on a calendar* (Weekly / Monthly on day / Monthly last day), or *Repeats after done*.
  - Calendar kinds show a live preview of the next dates.
  - The Start date defaults to today.
- **Edit a One-off Task:** title, notes, due date. The due date goes through Move, with the date picker's min = today.
- **Edit an Occurrence:**
  - covers only its due date (through Move) and Done;
  - shows the Recurring Task's title, notes and Schedule read-only, with an **Edit Recurring Task** action. That action covers title, notes, Schedule and **Stop**.
- **Rejections** (Move to a past day, Move of a Done Card, unknown id, editing a Stopped Recurring Task) re-render the Board with a short notice.
- **Midnight with the page open:** nothing happens until the next request. There is no client timer.

## 4. Architecture

Decided in:
- [How the reference renders HTML in the browser, and how drag-and-drop fits](https://github.com/svene/2026-10-24_taskapp/issues/3)
- [Core package layout and Handler / SPI names](https://github.com/svene/2026-10-24_taskapp/issues/11)
- the map's Notes

Follow the reference [svene/2026-07-26_quarkus-hexagon-demo-mm-per-tech](https://github.com/svene/2026-07-26_quarkus-hexagon-demo-mm-per-tech) unless stated otherwise.

### 4.1 Modules and packages

- Quarkus, Java 25, Maven, groupId `org.svenehrke`. Modules:
  - `core`
  - `inbound-http-html`
  - `outbound-sqlite`
  - `app-server`, the only deployable
- Base package `org.svenehrke.taskapp`. It contains the flat packages `card`, `recurring` and `board`, and the adapters use the same package names.
- Allowed dependencies: `board` → `card`, `recurring`; `card` → `recurring`. `recurring` never depends on `card`, so Occurrence inserts and adoption go through `RecurringTaskRepositorySPI`.
- ArchUnit rules:
  - no package cycles
  - Handlers never call Handlers
  - inbound adapters don't use SPIs

### 4.2 Ports

**Handlers:**
- `BoardHandler.board()`: catch-up, then the Board.
- `CardHandler`:
  - `createOneOff(title, notes, dueOn)`
  - `editOneOff(id, title, notes)`
  - `move(id, target, before?)`
  - `tick(id)`
  - `untick(id)`
- `RecurringTaskHandler`:
  - `create(title, notes, schedule)`
  - `edit(id, title, notes, schedule)`
  - `stop(id)`

**`OccurrenceProducer`** (`recurring`, `@ApplicationScoped`, not a Handler):
- `catchUp(today)`
- `afterDone(occurrence, doneOn)`
- `reproduce(task, today)`

Stale detection also lives in `recurring`.

**SPIs:**
- `CardRepositorySPI`, which also holds the Board's read queries
- `RecurringTaskRepositorySPI`

They are implemented by `CardService` and `RecurringTaskService` in `outbound-sqlite`. Method signatures are left to implementation.

**Board result:**
- The domain `Board` contains Today with its Overdue box, 13 more days, and Future's top 5 plus a count.
- Its `BoardCard`s carry `overdue`, `stale`, `rescheduled`, `done` and the Recurring Task id.
- `inbound-http-html` maps the `Board` to `BoardVM` (routes, labels, absolute dates for the buttons).

### 4.3 Cross-cutting

- **Transactions:** `@Transactional` sits on Handler methods in `core`, which needs `jakarta.transaction-api`. This deviates from the reference, so record it in `docs/architecture`.
  - Commands return `void`.
  - The Receiver then calls `BoardHandler.board()` in a second transaction.
- **Clock:** `app-server` produces `Clock.systemDefaultZone()`. Tests use a fixed or mutable Clock. Receivers never pass a date.
- **Failures:**
  - Malformed input becomes the sealed `ParsedOneOffTask` / `ParsedRecurringTask` (valid | `Invalid`), parsed in the Receiver. The form is re-rendered with messages.
  - Domain rejections throw `RejectedException(reason)`.

### 4.4 Rendering

- The server never renders HTML. A static shell loads htmx 4, Bulma and the esbuild bundle `hx-hono.js`, and `#app` does `hx-get` on load.
- View endpoints return JSON `{route, vm}` (`UiResponse`). A global htmx 4 extension, `hono`, renders the JSON with `render(route, vm)` in `htmx_after_request`.
- `*VM` records and the `UiRoute` enum are Java, and typescript-generator writes `vm-types.d.ts` from them. `routes.ts` uses `satisfies Record<UiRoute, …>`, so `tsc` fails the build when a route has no template. frontend-maven-plugin runs esbuild.
- Mutations return the Board envelope (200, or 400/409 with errors). Never 204.
- **Drag-and-drop:**
  - SortableJS 1.15.7, vendored or bundled (no CDN).
  - Each day Column is a `<form>`, and each Card has a hidden `taskId` input. Never name it `id`: that shadows `form.id` and breaks the morph.
  - Trigger on `add, update`, never `end`.
  - Stable ids on the board, Columns and Cards. Swap `#board` with `innerMorph`.
  - The drop handler posts `POST /cards/{id}/move` with `target` and `before`, derived from the drop.
  - `forceFallback: true` was needed for Chrome automation in the prototype.

## 5. Persistence

Decided in:
- [Task and Occurrence table schema](https://github.com/svene/2026-10-24_taskapp/issues/10)
- [ADR 0001](adr/0001-sqlite-instead-of-postgres.md)
- [Schedule representation and persistence](https://github.com/svene/2026-10-24_taskapp/issues/6)

- **SQLite**, one file. Module `outbound-sqlite` uses plain JDBC + Flyway (`quarkus-jdbc-sqlite`). No container.
- Dates are ISO text with `check (x = date(x))`, bound as strings. Every table is `STRICT`.
- Each connection runs `PRAGMA foreign_keys = ON`, WAL and `busy_timeout`. The Agroal pool size is 1.
- One `card` table holds One-off Tasks and Occurrences (`recurring_task_id` null = One-off). The card reference in URLs is its `id`.
- Facts only, no status column:
  - Done = `done_on` not null;
  - Overdue, Stale and rescheduled (`due_on <> original_due_on`) are derived.
- `weekdays` is a 7-bit bitmask (Mon = 1 … Sun = 64).
- No audit timestamps, no version column, no cascade (nothing is deleted).
- **Migration** `V1__initial_schema.sql`, in full, as it stands in the [resolution of the table-schema ticket](https://github.com/svene/2026-10-24_taskapp/issues/10):

```sql
create table recurring_task (
    id integer primary key,
    title text not null check (trim(title) <> ''),
    notes text,
    starts_on text not null check (starts_on = date(starts_on)),
    stopped_on text check (stopped_on = date(stopped_on)),
    kind text not null check (kind in ('WEEKLY','MONTHLY_DAY','MONTHLY_LAST','AFTER_DONE')),
    every_weeks integer, weekdays integer, month_day integer,
    after_amount integer, after_unit text,
    produced_through text check (produced_through = date(produced_through)),
    check (kind <> 'WEEKLY' or (every_weeks >= 1 and weekdays between 1 and 127
        and month_day is null and after_amount is null and after_unit is null and produced_through is not null)),
    check (kind <> 'MONTHLY_DAY' or (month_day between 1 and 28
        and every_weeks is null and weekdays is null and after_amount is null and after_unit is null and produced_through is not null)),
    check (kind <> 'MONTHLY_LAST' or (every_weeks is null and weekdays is null and month_day is null
        and after_amount is null and after_unit is null and produced_through is not null)),
    check (kind <> 'AFTER_DONE' or (after_amount >= 1 and after_unit in ('DAYS','WEEKS')
        and every_weeks is null and weekdays is null and month_day is null and produced_through is null))
) strict;

create table card (
    id integer primary key,
    recurring_task_id integer references recurring_task (id),
    title text, notes text,
    due_on text not null check (due_on = date(due_on)),
    original_due_on text not null check (original_due_on = date(original_due_on)),
    position integer not null check (position >= 0),
    done_on text check (done_on = date(done_on)),
    check ((recurring_task_id is null and trim(title) <> '')
        or (recurring_task_id is not null and title is null and notes is null)),
    unique (recurring_task_id, original_due_on)
) strict;

create index card_due_on_position on card (due_on, position);
```

## 6. Running

Decided in [Run setup for the built app](https://github.com/svene/2026-10-24_taskapp/issues/12).

- **Dev:** `quarkus dev` on port 8080. The `%dev` DB is `app-server/.data/dev.db` (gitignored).
- **Built:**
  - Quarkus uber-jar, no native build.
  - `install.sh` builds it and copies it to `~/.local/share/taskapp/taskapp.jar`, and copies `bin/taskapp` to `~/.local/bin/taskapp`. The script is `exec java -jar … "$@"`.
  - Upgrading = run `install.sh` again. Java 25 is expected on `PATH`.
- **Start:** `taskapp`, by hand, in the foreground, logging to the console. Ctrl-C stops it. No autostart, no browser auto-open.
- **Network:** binds `127.0.0.1:7070` only (override via `QUARKUS_HTTP_PORT`). A busy port fails startup.
- **DB path:** `taskapp.db.path` (env `TASKAPP_DB_PATH`), default `~/.local/share/taskapp/taskapp.db`. The parent directory is created if missing. Tests use a fresh temp file.
- **Backup:** on every startup, before Flyway migrates, the app writes `VACUUM INTO ~/.local/share/taskapp/backups/taskapp-<timestamp>.db` and keeps the last 7.

## 7. Testing and docs

- Unit tests on `core`, with a fixed or mutable `Clock`.
- `@QuarkusTest` flow tests in `app-server`, against a temp SQLite file.
- ArchUnit stays green.
- `concepts.md` and `docs/architecture` as in the reference.
