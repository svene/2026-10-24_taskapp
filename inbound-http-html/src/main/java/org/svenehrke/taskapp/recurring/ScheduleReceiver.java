package org.svenehrke.taskapp.recurring;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.svenehrke.taskapp.html.UiResponse;
import org.svenehrke.taskapp.html.UiRoute;

/**
 * The New Task modal's live Schedule preview: the next dates of the Schedule fields as typed, computed by core. It
 * reads nothing stored, so it calls no Handler. Answers 200 with a message when the fields don't make a Schedule yet.
 */
@Path("/schedules")
@Produces(MediaType.APPLICATION_JSON)
public class ScheduleReceiver {

    /** The New Task modal's {@code repeats} choice for an After-done Schedule; its {@code kind} select is then ignored. */
    public static final String AFTER_DONE = "afterDone";

    @GET
    @Path("preview")
    public UiResponse preview(@QueryParam("repeats") String repeats, @QueryParam("kind") String kind,
        @QueryParam("weekdays") List<String> weekdays, @QueryParam("everyWeeks") String everyWeeks,
        @QueryParam("monthDay") String monthDay, @QueryParam("startsOn") String startsOn,
        @QueryParam("afterAmount") String afterAmount, @QueryParam("afterUnit") String afterUnit) {
        return UiResponse.of(UiRoute.SchedulePreview, SchedulePreviewVM.of(
            input(repeats, kind, weekdays, everyWeeks, monthDay, startsOn, afterAmount, afterUnit)));
    }

    /** The modal's Schedule fields: {@code repeats=afterDone} makes it {@link ParsedSchedule#AFTER_DONE}, else {@code kind}. */
    public static ParsedSchedule.Input input(String repeats, String kind, List<String> weekdays, String everyWeeks,
        String monthDay, String startsOn, String afterAmount, String afterUnit) {
        return new ParsedSchedule.Input(AFTER_DONE.equals(repeats) ? ParsedSchedule.AFTER_DONE : kind, weekdays,
            everyWeeks, monthDay, startsOn, afterAmount, afterUnit);
    }
}
