# PROTOTYPE: Board look and behaviour

Throwaway. Answers [Board look and behaviour](https://github.com/svene/2026-10-24_taskapp/issues/5). Never merged to `main`.

```sh
cd prototypes/board && bun install && bun run start   # http://localhost:3005
```

Three layouts of the same Board, switched with `?variant=A|B|C`, the black bar at the bottom, or the ← → keys:

| Variant | Layout |
|---|---|
| A, Scroll strip | 15 Columns side by side with horizontal scroll; Today pinned left |
| B, Two weeks | two rows of 7 day Columns, with Future as a sidebar |
| C, Focus + agenda | a wide Today with full cards and notes, the next 13 days as a vertical agenda, then Future |

Behaviour is the same in every variant:

- **Drag** a card to another day to reschedule it (the card then shows ⇢ and its original due date). Drag within a day to reorder. Drag onto Future to move it to the day after the span. Done cards don't drag.
- **Overdue** cards sit in a red box at the top of Today. You can drag them out of the box, but nothing can be dropped into it.
- **Buttons on a card** (shown on hover in compact modes): →Today (Overdue only), +1d, +1w, ✎ edit. These are the non-drag fallback.
- **☐ / ☑** ticks a card off or back on. Ticking a "Repeats after done" Occurrence creates the next one.
- **+** in a Column header opens the New task modal, with the due date set to that day. The "+ add" field at the bottom of a Column quick-adds a One-off Task.
- The **modal** offers One-off, Repeats on a calendar (weekly on chosen weekdays every N weeks, or monthly on a day) and Repeats after done (N days or weeks). It shows a live preview of the next dates. Editing an Occurrence also edits its Recurring Task (title, notes, Schedule) and offers **Stop**.
- **Stale**: if you edit a Schedule or Stop a Recurring Task, open future Occurrences it no longer produces get a dashed yellow edge and a Stale tag.
- **Top bar**: "+1 day" and "+1 week" simulate time passing (Columns roll, cards become Overdue, Occurrences fill in); "reset" restores the seed data.

The server keeps all state in memory, and every action re-renders the whole app (`#app`, `innerMorph`). One deviation from the real stack: Hono `html` templates render here on the server instead of in the browser.
