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

    /** Every Card, open or Done, due on {@code day}, ordered by position, id. */
    List<Card> dueOn(LocalDate day);

    /** A Move's write: the Card's due date and position; nothing else changes. */
    void setDueOnAndPosition(long id, LocalDate dueOn, int position);

    /** An edit's write: a One-off Task's title and notes; nothing else changes. */
    void setTitleAndNotes(long id, String title, String notes);

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
