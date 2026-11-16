package org.svenehrke.taskapp.board;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.List;
import java.util.Objects;
import org.svenehrke.taskapp.board.BoardVM.EditRecurringVM;
import org.svenehrke.taskapp.html.UiResponse;
import org.svenehrke.taskapp.html.UiRoute;
import org.svenehrke.taskapp.recurring.ParsedRecurringTask;
import org.svenehrke.taskapp.recurring.RecurringTaskHandler;
import org.svenehrke.taskapp.recurring.SchedulePreviewVM;
import org.svenehrke.taskapp.recurring.ScheduleReceiver;

/**
 * Editing and stopping a Recurring Task (spec §2.4), reached from the Occurrence modal. It lives in {@code board}
 * for the same reason as {@link CardReceiver}: every response is the Board. An unknown or Stopped task, or a switch
 * between calendar and After-done, is rejected with a notice (409, {@link RejectedExceptionMapper}).
 */
@RunOnVirtualThread
@Path("/recurring-tasks")
@Produces(MediaType.APPLICATION_JSON)
public class RecurringTaskReceiver {

    @Inject
    BoardHandler boardHandler;

    @Inject
    RecurringTaskHandler recurringTaskHandler;

    /** The Board with the Recurring Task modal open. */
    @GET
    @Path("{id}/edit")
    public UiResponse edit(@PathParam("id") long id) {
        var task = recurringTaskHandler.recurringTask(id);
        return UiResponse.of(UiRoute.BoardPage, BoardVM.of(boardHandler.board(), EditRecurringVM.of(task)));
    }

    /**
     * The modal's save: title, notes and Schedule ({@code repeats} is the task's family, a hidden field). Invalid
     * input re-opens the modal with its messages (400).
     */
    @POST
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response save(@PathParam("id") long id, @FormParam("repeats") String repeats, @FormParam("title") String title,
        @FormParam("notes") String notes, @FormParam("kind") String kind, @FormParam("weekdays") List<String> weekdays,
        @FormParam("everyWeeks") String everyWeeks, @FormParam("monthDay") String monthDay,
        @FormParam("startsOn") String startsOn, @FormParam("afterAmount") String afterAmount,
        @FormParam("afterUnit") String afterUnit) {
        var schedule = ScheduleReceiver.input(repeats, kind, weekdays, everyWeeks, monthDay, startsOn, afterAmount, afterUnit);
        return switch (ParsedRecurringTask.parse(title, notes, schedule)) {
            case ParsedRecurringTask.Valid task -> {
                recurringTaskHandler.edit(id, task.title(), task.notes(), task.schedule());
                yield UiResponse.response(Status.OK, UiRoute.BoardPage, BoardVM.of(boardHandler.board()));
            }
            case ParsedRecurringTask.Invalid invalid -> {
                var stored = EditRecurringVM.of(recurringTaskHandler.recurringTask(id));
                var form = new EditRecurringVM(id, stored.repeats(), orEmpty(title), orEmpty(notes), orEmpty(kind),
                    CardReceiver.weekdayNumbers(weekdays), orEmpty(everyWeeks), orEmpty(monthDay), orEmpty(startsOn),
                    orEmpty(afterAmount), orEmpty(afterUnit), invalid.errors(), SchedulePreviewVM.of(schedule));
                yield UiResponse.response(Status.BAD_REQUEST, UiRoute.BoardPage, BoardVM.of(boardHandler.board(), form));
            }
        };
    }

    /** The modal's Stop: the task produces no more Occurrences; its open future ones turn Stale. */
    @POST
    @Path("{id}/stop")
    public UiResponse stop(@PathParam("id") long id) {
        recurringTaskHandler.stop(id);
        return UiResponse.of(UiRoute.BoardPage, BoardVM.of(boardHandler.board()));
    }

    private static String orEmpty(String value) {
        return Objects.requireNonNullElse(value, "");
    }
}
