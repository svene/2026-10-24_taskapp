package org.svenehrke.taskapp.card;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Outbound port: the {@code card} table, including the Board's read queries. */
public interface CardRepositorySPI {

    Optional<Card> find(long id);

    /** Ticks the Card off on {@code doneOn}, or makes it open again when {@code doneOn} is null. */
    void setDoneOn(long id, LocalDate doneOn);

    /** The highest {@code position} on that day, if any Card is due on it. */
    OptionalInt maxPosition(LocalDate dueOn);

    /** Inserts a One-off Task; its original due date is {@code dueOn}. */
    void insertOneOff(String title, String notes, LocalDate dueOn, int position);

    /** Every Card, open or Done, due from {@code from} through {@code through}, ordered by due date, position, id. */
    List<Card> dueBetween(LocalDate from, LocalDate through);

    /**
     * The Overdue box of Today: every Card due before {@code today} that is open, or was ticked off today (it stays
     * struck through until the end of that day). Ordered by due date, position, id.
     */
    List<Card> overdueBox(LocalDate today);

    /** Every open Card due on or after {@code from}, ordered by due date, position, id. */
    List<Card> openDueFrom(LocalDate from);
}
