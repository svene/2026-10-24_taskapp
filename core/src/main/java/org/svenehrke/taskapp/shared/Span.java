package org.svenehrke.taskapp.shared;

import java.time.LocalDate;

/**
 * The Board's span: Today plus 13 more days. Future starts the day after it. Here, not in {@code board}, because a
 * Move to {@code future} ({@code card}) needs that day too, and {@code card} must not depend on {@code board}.
 */
public final class Span {

    public static final int DAYS = 14;

    private Span() {}

    /** The day after the span, where Future starts. */
    public static LocalDate futureFrom(LocalDate today) {
        return today.plusDays(DAYS);
    }
}
