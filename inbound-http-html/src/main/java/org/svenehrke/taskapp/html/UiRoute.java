package org.svenehrke.taskapp.html;

/**
 * Every view the browser-side hono templates can render. typescript-generator turns this enum into the
 * {@code UiRoute} string union in {@code generated/vm-types.d.ts}, and {@code routes.ts} must have an entry
 * for each value, so a route added or renamed here is a TypeScript error until its template exists.
 */
public enum UiRoute {
    BoardPage,
    /** The New Task modal's next dates of the chosen calendar Schedule, swapped into its preview box. */
    SchedulePreview,
}
