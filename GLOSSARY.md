# Taskapp

A personal kanban board for keeping track of one's own private tasks, both one-off and recurring.

## Language

**Task**:
Something the owner intends to get done.
_Avoid_: Todo, item, ticket

**One-off Task**:
A Task that is done exactly once; once done, it leaves the active Board.
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
One instance of a Recurring Task for one due date; it moves through the Columns like a One-off Task.
_Avoid_: Instance, iteration, card

**Due date**:
The day by which a Task should be done; optional for a One-off Task, always set for an Occurrence.
_Avoid_: Deadline

**Overdue**:
A One-off Task or Occurrence whose due date has passed and that is not done.
_Avoid_: Late, missed

**Board**:
The kanban view of active One-off Tasks and Occurrences, arranged in Columns.
_Avoid_: Dashboard, list

**Column**:
One stage of a Board that a Task moves through.
_Avoid_: Lane, status, swimlane, backlog
