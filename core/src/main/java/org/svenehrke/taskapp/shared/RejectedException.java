package org.svenehrke.taskapp.shared;

/**
 * A command the domain refuses (spec §4.3): an unknown id, a Move to a past day, a Move of a Done Card, editing a
 * Stopped Recurring Task. {@code reason} is the short notice the Board shows. Thrown inside a Handler's transaction,
 * so nothing the command did is kept.
 */
public class RejectedException extends RuntimeException {

    public RejectedException(String reason) {
        super(reason);
    }

    public String reason() {
        return getMessage();
    }
}
