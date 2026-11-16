# Architecture

Taskapp follows the reference
[svene/2026-07-26_quarkus-hexagon-demo-mm-per-tech](https://github.com/svene/2026-07-26_quarkus-hexagon-demo-mm-per-tech);
the overview is in [`concepts.md`](../../concepts.md). This directory records where Taskapp deviates from the
reference, and grows as the slices of [Taskapp v1](https://github.com/svene/2026-10-24_taskapp/issues/14) land.

## Deviations from the reference

### `@Transactional` on Handler methods in `core`, not on `*Service`

The reference puts `@Transactional` on the `*Service` methods of its outbound adapters. Taskapp puts it on the
Handler methods in `core`, which therefore depends on `jakarta.transaction-api` (the annotation only; the
interceptor comes from `quarkus-narayana-jta` in `outbound-sqlite`).

Why: one use case spans several SPI calls that must commit together. A Move renumbers a day and updates the Card;
the Board's lazy catch-up inserts Occurrences and advances `produced_through` before reading. Only the Handler sees
the whole use case.

Consequences:
- Commands return `void`. The Receiver then calls `BoardHandler.board()`, its own transaction (catch-up, then the
  read). That is two transactions per mutating request.
- Decided in [Core package layout and Handler / SPI names](https://github.com/svene/2026-10-24_taskapp/issues/11).

### Flyway runs from `DatabaseStartup`, not `migrate-at-start`

SQLite creates its file but not the directories above it. `DatabaseStartup` (`outbound-sqlite`) creates the
parent directory of `taskapp.db.path` on startup and then migrates, so `quarkus.flyway.migrate-at-start` is off.
The startup backup ([Run setup](https://github.com/svene/2026-10-24_taskapp/issues/25)) goes there too, before
the migration.

### SQLite PRAGMAs via the JDBC URL

`foreign_keys`, `journal_mode=wal` and `busy_timeout` are query parameters of the JDBC URL. sqlite-jdbc runs them
as PRAGMAs on every connection it opens. The Agroal pool has one connection (SQLite has one writer at a time).

### Card-creating Receivers live in `board`

`CardReceiver` (create, quick-add, open the New Task modal) is in package `board`, not `card`. Every response is
the whole Board, so it calls `BoardHandler`, and `card` must not depend on `board` (`board` → `card` already; the
cycle check in `ArchitectureTest` would fail).

### Future carries its Cards, not just a count

Spec §4.2 has Future show the 5 nearest open Cards "plus a count". `Board.Future` carries the rest as a list
(`more`) instead, so "+N more" expands in the browser (a `<details>`) without another request.

### Board updates morph `#app`

Requests from inside the Board answer with the whole Board, swapped into `#app` with `innerMorph`
(`hx-target:inherited` / `hx-swap:inherited` on `#board`). Morph keeps focus and scroll, but leaves the value of
the focused input alone, so a quick-add form resets itself after a successful create (`hx-on::after:swap`).
The New Task modal is Board state (`BoardVM.newCard`) and closes client-side by removing itself.
