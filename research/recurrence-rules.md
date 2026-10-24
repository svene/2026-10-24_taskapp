# Research: options for expressing recurrence rules

Ticket: [#4 Options for expressing recurrence rules](https://github.com/svene/2026-10-24_taskapp/issues/4) (map: #1).
Researched 2026-10-24. Baseline from #1: single-user local kanban, Quarkus + Java 25, Postgres via plain JDBC + Flyway;
Recurring Tasks need **calendar-based schedules** (every Monday, 1st of month) and **interval-after-completion**
(3 weeks after last done).

This note presents options and trade-offs. It does not decide.

## TL;DR

- **No standard covers interval-after-completion.** RFC 5545 RRULE, JSCalendar (RFC 8984) and the IETF task
  extensions draft all anchor recurrence to a start date (`DTSTART`), never to a completion time. Whatever is chosen,
  "3 weeks after last done" is app-defined logic.
- **Calendar-based schedules are well covered** by RFC 5545 RRULE, and two maintained-enough Java libraries
  evaluate them: **ical4j 4.4.0** (released 2026-10-02, active, `java.time`-native) and **lib-recur 0.17.1**
  (last release 2024-04-07, no commits since, own date type). Both ran correctly on JDK 25 in a throwaway probe.
- A **hand-rolled model** covering the stated examples is small (`java.time` arithmetic), but grows fast if the
  calendar side ever needs "last weekday", "2nd Tuesday", "every 2 weeks on Mon+Thu", etc.
- **Persistence** is cheap in every option: an RRULE is one `text` column; interval-after-completion is a
  count + unit (or an ISO-8601 period string). The real persistence question is the discriminator between the two
  kinds and what anchor date is stored (start/last-done/next-due).

## 1. What the standards say

### RFC 5545 RRULE (iCalendar)

- `RECUR` value grammar: `FREQ` (required: SECONDLY…YEARLY), optional `UNTIL` xor `COUNT`, `INTERVAL`, and `BY*`
  parts (`BYDAY`, `BYMONTHDAY`, `BYYEARDAY`, `BYWEEKNO`, `BYMONTH`, `BYSETPOS`, …), plus `WKST`.
  [RFC 5545 §3.3.10](https://www.rfc-editor.org/rfc/rfc5545#section-3.3.10)
- `RRULE` may appear in `VTODO` (tasks), not only `VEVENT`. The recurrence set is generated from `DTSTART` plus
  `RRULE`/`RDATE` minus `EXDATE`; `DTSTART` "defines the first instance" and SHOULD be synchronized with the rule.
  [RFC 5545 §3.8.5.3](https://www.rfc-editor.org/rfc/rfc5545#section-3.8.5.3)
- Invalid dates produced by a rule (e.g. Feb 30 from `BYMONTHDAY=30`) MUST be ignored — relevant for "on the 31st".
  Example in RFC 5545 §3.8.5.3 (`FREQ=MONTHLY;BYMONTHDAY=15,30`). Use `BYMONTHDAY=-1` for "last day of month".
- `VTODO` has a `COMPLETED` property (§3.8.2.1), but nothing in RFC 5545 relates recurrence to it.
- RFC 7529 adds `RSCALE` and `SKIP` (OMIT/BACKWARD/FORWARD) for non-Gregorian calendars and invalid-day handling.
  [RFC 7529](https://www.rfc-editor.org/rfc/rfc7529)

Example rules for the stated cases: every Monday `FREQ=WEEKLY;BYDAY=MO`; 1st of month `FREQ=MONTHLY;BYMONTHDAY=1`;
last day of month `FREQ=MONTHLY;BYMONTHDAY=-1`; 2nd Tuesday `FREQ=MONTHLY;BYDAY=2TU`.

### Interval-after-completion: no standard

- `FREQ=WEEKLY;INTERVAL=3` means "every 3 weeks counted from DTSTART", not "3 weeks after completion". The probe
  below shows it producing 2026-11-14, 2026-12-05, … regardless of when the task is done.
- JSCalendar (RFC 8984) `recurrenceRules` mirrors RRULE (`frequency`, `interval`, `rscale`, `skip`, `by*`) and has
  no completion-relative form. [RFC 8984 §4.3.3](https://www.rfc-editor.org/rfc/rfc8984#section-4.3.3)
- `draft-ietf-calext-ical-tasks-17` ("Task Extensions to iCalendar", in the RFC Editor queue as of 2026-10-23) does
  not mention recurrence at all (text search for "recur" returns nothing).
  [datatracker](https://datatracker.ietf.org/doc/draft-ietf-calext-ical-tasks/)

Consequence: it can be emulated with RRULE by re-seeding `DTSTART := completion date` and taking the first
occurrence after it, but that is just `completedOn.plus(3, WEEKS)` with extra steps. Any interoperable `.ics`
export of such a task would need an `X-` property; out of scope for v1 anyway (no export in #1).

## 2. Option A — RRULE string evaluated by ical4j

| Fact | Value | Source |
|---|---|---|
| Coordinates | `org.mnode.ical4j:ical4j` | [Maven Central](https://repo1.maven.org/maven2/org/mnode/ical4j/ical4j/) |
| Latest | 4.4.0, 2026-10-02 (4.3.0 2026-06-27, 4.2.5 2026-04-03, 4.2.4 2026-03-21) | Maven Central dir listing; [GitHub releases](https://github.com/ical4j/ical4j/releases) |
| Activity | last push 2026-10-24; 49 open issues; not archived | GitHub API `repos/ical4j/ical4j` |
| License | BSD-3-Clause | GitHub |
| Java | 4.x requires Java 11+; bytecode is class version 55 (Java 11); ships `module-info` | [README](https://github.com/ical4j/ical4j#readme), jar inspection |
| Deps (compile) | slf4j-api, commons-lang3, commons-codec, threeten-extra; caffeine/jparsec/groovy optional runtime | `ical4j-4.4.0.pom` |
| Recurrence API | `Recur<T extends Temporal>` (`new Recur<LocalDate>("FREQ=…")`), `getNextDate(seed, start)`, `getDates(seed, from, to[, max])`, `getDatesAsStream`, `Recur.Builder` | `Recur.java` in 4.4.0 sources jar |
| Standards | RFC 5545, 7529 (RSCALE/SKIP) and more | README |

Fit:
- Calendar schedules: full RRULE. `LocalDate` works directly, so all-day/date-only tasks avoid time-zone machinery.
- Interval-after-completion: not expressible; app logic.
- Quarkus: plain library, no Quarkus extension (none found in quarkiverse). JVM mode is all #1 needs. The README
  documents a time-zone cache setting (`net.fortuna.ical4j.timezone.cache.impl`) that must be set if
  `javax.cache` is absent; the date-only probe below needed none of it, but zoned date-times would.
- Cost: a full iCalendar parser/model pulled in to use one class; ~4 transitive jars.

## 3. Option B — RRULE string evaluated by lib-recur (dmfs)

| Fact | Value | Source |
|---|---|---|
| Coordinates | `org.dmfs:lib-recur` (+ `org.dmfs:rfc5545-datetime` 0.3 from 2020-05, `org.dmfs:jems2` 2.23.1) | [Maven Central](https://repo1.maven.org/maven2/org/dmfs/lib-recur/), `lib-recur-0.17.1.pom` |
| Latest | 0.17.1, 2024-04-07 (0.17.0 2024-04-06, 0.16.0 2024-03) | Maven Central, [GitHub releases](https://github.com/dmfs/lib-recur/releases) |
| Activity | last push 2024-04-07 (about 2.5 years idle); 27 open issues; not archived | GitHub API `repos/dmfs/lib-recur` |
| License | Apache-2.0 | GitHub |
| Java | class version 52 (Java 8); no `module-info`, no `Automatic-Module-Name` | jar inspection |
| API | `new RecurrenceRule("FREQ=…")`, `RecurrenceSet` via `OfRule`/`OfRuleAndFirst`, `Merged`, `Difference`, `FastForwarded`, `Within`, `LastInstance`; strict/lax `RfcMode`; rule builder | [README](https://github.com/dmfs/lib-recur#readme) |
| Caveats | README: "interface … not finalized yet and subject to change"; uses its own `org.dmfs.rfc5545.DateTime`, not `java.time` (adapter code needed); 0-based months in `DateTime` constructor | README |

Fit:
- Calendar schedules: full RRULE incl. RSCALE, focused library (recurrence only, small).
- Interval-after-completion: not expressible; app logic.
- Quarkus/Java 25: plain jar, runs on JDK 25 (probe). Maintenance risk is the main concern.

## 4. Option C — hand-rolled rule model in `core`

A sealed interface of records, e.g. (sketch only, names not decided):

```java
sealed interface RecurrenceRule {
  record Weekly(Set<DayOfWeek> days) implements RecurrenceRule {}
  record MonthlyOnDay(int dayOfMonth /* -1 = last */) implements RecurrenceRule {}
  record AfterCompletion(int amount, ChronoUnit unit) implements RecurrenceRule {}
}
```

Fit:
- Calendar schedules: exactly the cases modelled; each new pattern (every 2nd week, 2nd Tuesday, weekdays,
  yearly) is new code + tests. Edge cases (31st in short months, week start, leap years) must be decided and tested
  by hand — RFC 5545 already decides them.
- Interval-after-completion: trivial (`lastDone.plus(amount, unit)`), first-class in the type.
- No dependencies; pure domain types suit the hexagon (`core` stays library-free, unit-testable).
- Persistence: needs its own encoding (columns per variant, or a JSON/`jsonb` column, or a compact string).

## 5. Option D — hybrid (RRULE for calendar, own type for after-completion)

A sealed `RecurrenceRule` with two variants: `CalendarSchedule(String rrule)` delegated to ical4j or lib-recur
behind a `core`-owned interface, and `AfterCompletion(Period)`. Gets the standard's coverage and edge-case semantics
for the calendar kind while modelling the non-standard kind explicitly. Cost: a library dependency and a seam to keep
it out of the domain types (or accept it in `core`).

## 6. Comparison

| | Calendar schedules | After completion | Dependency / maintenance | Java 25 | Persistence |
|---|---|---|---|---|---|
| A ical4j | full RRULE | app logic | active, 4 deps, large lib | yes (probe) | `text` RRULE |
| B lib-recur | full RRULE (+RSCALE) | app logic | idle since 2024-04, unstable API, own DateTime | yes (probe) | `text` RRULE |
| C hand-rolled | only modelled cases | first-class | none | n/a | own encoding |
| D hybrid | full RRULE | first-class | as A or B | yes | `kind` + `text` / period |

## 7. Persistence implications (plain JDBC + Flyway)

- RRULE fits in one `text` column, parsed on read; validate on write by constructing the library's rule object.
  Postgres has no built-in RRULE type or function; evaluating in Java keeps the DB a plain store (any
  third-party Postgres extension would also have to be in the Dev Services / compose image).
- Interval-after-completion: `amount int` + `unit text` (`DAYS|WEEKS|MONTHS`), or an ISO-8601 period string
  (`P3W`) parsed by `java.time.Period.parse` — note `Period.parse("P3W")` normalises to 21 days.
- A discriminator (`recurrence_kind`) or nullable columns per kind; a check constraint can enforce that exactly
  one is set.
- Anchor dates: RRULE needs a seed (`DTSTART` equivalent — e.g. task creation date or first due date);
  after-completion needs the last completion date. Whether "next due" is stored (materialised when a task is
  completed) or computed on read ties into the open scheduler question in #1.
- Date-only (`LocalDate` / Postgres `date`) sidesteps time zones; RRULE with times would pull in zone handling.

## 8. Probe (throwaway, JDK 25, not committed)

Both libraries on the classpath with `java Probe.java`, seed 2026-10-24 (Saturday):

```
ical4j    FREQ=WEEKLY;BYDAY=MO       next 2026-10-26; Nov-Dec [11-02, 11-09, …, 12-28]
lib-recur FREQ=WEEKLY;BYDAY=MO       20261026 20261102 20261109 20261116
ical4j    FREQ=MONTHLY;BYMONTHDAY=1  next 2026-11-01; Nov-Dec [11-01, 12-01]
lib-recur FREQ=MONTHLY;BYMONTHDAY=1  20261101 20261201 20270101 20270201
ical4j    FREQ=MONTHLY;BYMONTHDAY=-1 next 2026-10-31; Nov-Dec [11-30, 12-31]
lib-recur FREQ=MONTHLY;BYMONTHDAY=-1 20261031 20261130 20261231 20270131
ical4j    FREQ=WEEKLY;INTERVAL=3     next 2026-11-14; Nov-Dec [11-14, 12-05, 12-26]   <- seed-anchored, not completion
lib-recur FREQ=WEEKLY;INTERVAL=3     20261024 20261114 20261205 20261226
```

ical4j logged only an SLF4J "no provider" warning (Quarkus supplies one). Note lib-recur `OfRule` includes the seed
when it matches the rule.

## 9. Open questions surfaced (for the owner)

- **Which representation** (A/B/C/D) — the decision this research feeds.
- **Anchor semantics**: does a calendar-schedule task's next occurrence count from the schedule regardless of when it
  was completed (missed Mondays skipped?), and what happens on completing early/late? RRULE answers "which dates",
  not "which one is next after an overdue completion".
- **Date-only vs time-of-day** for schedules — determines whether time zones matter (#1 already lists time-zone
  handling as unspecified).
- **Stored next-due vs computed on read** — overlaps the scheduler question in #1.
- **How the user enters a rule in the UI** (preset picker vs free RRULE) — bounds how much RRULE coverage is useful.
