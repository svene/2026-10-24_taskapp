package org.svenehrke.taskapp.card;

import java.time.LocalDate;

/**
 * A row of the {@code card} table: a One-off Task ({@code recurringTaskId} null) or an Occurrence.
 * {@code doneOn} is null while the Card is open.
 */
public record Card(
    long id,
    Long recurringTaskId,
    String title,
    String notes,
    LocalDate dueOn,
    LocalDate originalDueOn,
    int position,
    LocalDate doneOn
) {
    public boolean done() {
        return doneOn != null;
    }
}
