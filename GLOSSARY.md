# Taskapp

A personal, calendar-style board for keeping track of one's own private tasks, both one-off and recurring.

## Language

**Task**:
Something the owner intends to get done.
_Avoid_: Todo, item, ticket

**One-off Task**:
A Task that is done exactly once.
_Avoid_: One-time task, single task

**Recurring Task**:
A Task defined by a schedule; it is never on the Board itself, but produces Occurrences that are.
_Avoid_: Periodical task, repeating task, routine, template

**Schedule**:
The rule of a Recurring Task that decides the due dates of its Occurrences: either calendar-based (e.g. every second Monday) or a fixed interval after the previous Occurrence is done.
_Avoid_: Recurrence, rule, cadence

**Stopped**:
A Recurring Task that produces no further Occurrences; Occurrences it already produced remain.
_Avoid_: Deleted, archived, cancelled

**Occurrence**:
One instance of a Recurring Task for one due date; once produced, it is ticked off and rescheduled like a One-off Task.
_Avoid_: Instance, iteration, card

**Due date**:
The day by which a One-off Task or Occurrence should be done; always set, and changed by rescheduling.
_Avoid_: Deadline

**Original due date**:
The due date a One-off Task was created with, or the date the Schedule produced for an Occurrence; unchanged by rescheduling.
_Avoid_: Planned date, initial date

**Stale Occurrence**:
An open Occurrence due after today whose original due date its Recurring Task's current Schedule would no longer produce, because the Schedule was edited or the Recurring Task was Stopped.
_Avoid_: Orphan, obsolete

**Overdue**:
A One-off Task or Occurrence whose due date has passed and that is not done.
_Avoid_: Late, missed

**Done**:
A One-off Task or Occurrence the owner has ticked off, on a particular day; unticking makes it open again and undoes nothing else. There is no deleting: a task the owner no longer wants is ticked off.
_Avoid_: Completed, closed, finished, deleted

**Board**:
The calendar-style view of One-off Tasks and Occurrences: one Column per day for a rolling span starting today, followed by Future.
_Avoid_: Dashboard, list, calendar, kanban

**Column**:
One vertical strip of the Board: either a single day, or Future.
_Avoid_: Lane, status, swimlane, backlog, stage

**Today**:
The first Column of the Board; it also shows every open Overdue One-off Task and Occurrence.

**Future**:
The last Column of the Board, showing the nearest few One-off Tasks and Occurrences due after the span.
_Avoid_: Later, backlog
