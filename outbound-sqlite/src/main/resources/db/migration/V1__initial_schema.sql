create table recurring_task (
    id integer primary key,
    title text not null check (trim(title) <> ''),
    notes text,
    starts_on text not null check (starts_on is date(starts_on)),
    stopped_on text check (stopped_on is date(stopped_on)),
    kind text not null check (kind in ('WEEKLY','MONTHLY_DAY','MONTHLY_LAST','AFTER_DONE')),
    every_weeks integer, weekdays integer, month_day integer,
    after_amount integer, after_unit text,
    produced_through text check (produced_through is date(produced_through)),
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
    due_on text not null check (due_on is date(due_on)),
    original_due_on text not null check (original_due_on is date(original_due_on)),
    position integer not null check (position >= 0),
    done_on text check (done_on is date(done_on)),
    check ((recurring_task_id is null and trim(title) <> '')
        or (recurring_task_id is not null and title is null and notes is null)),
    unique (recurring_task_id, original_due_on)
) strict;

create index card_due_on_position on card (due_on, position);
