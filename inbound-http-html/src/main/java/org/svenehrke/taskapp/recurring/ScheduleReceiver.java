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

    @GET
    @Path("preview")
    public UiResponse preview(@QueryParam("kind") String kind, @QueryParam("weekdays") List<String> weekdays,
        @QueryParam("everyWeeks") String everyWeeks, @QueryParam("monthDay") String monthDay,
        @QueryParam("startsOn") String startsOn) {
        return UiResponse.of(UiRoute.SchedulePreview,
            SchedulePreviewVM.of(new ParsedSchedule.Input(kind, weekdays, everyWeeks, monthDay, startsOn)));
    }
}
