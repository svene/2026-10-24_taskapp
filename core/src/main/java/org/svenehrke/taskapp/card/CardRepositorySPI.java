package org.svenehrke.taskapp.card;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;

/** Outbound port: the {@code card} table, including the Board's read queries. */
public interface CardRepositorySPI {

    /** The highest {@code position} on that day, if any Card is due on it. */
    OptionalInt maxPosition(LocalDate dueOn);

    /** Inserts a One-off Task; its original due date is {@code dueOn}. */
    void insertOneOff(String title, String notes, LocalDate dueOn, int position);

    /** Every Card, open or Done, due from {@code from} through {@code through}, ordered by due date, position, id. */
    List<Card> dueBetween(LocalDate from, LocalDate through);

    /** Every open Card due on or after {@code from}, ordered by due date, position, id. */
    List<Card> openDueFrom(LocalDate from);
}
