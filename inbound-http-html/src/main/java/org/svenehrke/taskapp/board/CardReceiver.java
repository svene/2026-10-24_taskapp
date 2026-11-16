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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.Objects;
import org.svenehrke.taskapp.board.BoardVM.NewCardVM;
import org.svenehrke.taskapp.board.BoardVM.QuickAddError;
import org.svenehrke.taskapp.card.CardHandler;
import org.svenehrke.taskapp.card.ParsedOneOffTask;
import org.svenehrke.taskapp.html.UiResponse;
import org.svenehrke.taskapp.html.UiRoute;

/**
 * Creating, ticking off and moving Cards. It lives in {@code board}, not {@code card}: every response is the Board, and {@code card}
 * must not depend on {@code board} (board → card already).
 */
@RunOnVirtualThread
@Path("/cards")
@Produces(MediaType.APPLICATION_JSON)
public class CardReceiver {

    @Inject
    CardHandler cardHandler;

    @Inject
    BoardHandler boardHandler;

    /** The Board with the New Task modal open, its due date preset (the Column's day). */
    @GET
    @Path("new")
    public UiResponse newCard(@QueryParam("dueOn") String dueOn) {
        var board = boardHandler.board();
        var preset = Objects.requireNonNullElse(dueOn, board.days().getFirst().date().toString());
        return UiResponse.of(UiRoute.BoardPage, BoardVM.of(board, NewCardVM.empty(preset), null));
    }

    /** The New Task modal's submit. Rejected input re-opens the modal with its messages (400). */
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response create(@FormParam("title") String title, @FormParam("notes") String notes,
        @FormParam("dueOn") String dueOn) {
        return switch (ParsedOneOffTask.parse(title, notes, dueOn)) {
            case ParsedOneOffTask.Valid task -> created(task);
            case ParsedOneOffTask.Invalid invalid -> UiResponse.response(Status.BAD_REQUEST, UiRoute.BoardPage,
                BoardVM.of(boardHandler.board(), new NewCardVM(orEmpty(title), orEmpty(notes), orEmpty(dueOn), invalid.errors()), null));
        };
    }

    /** Quick-add in a day Column: a title only, due that day. Rejected input shows its message in that Column (400). */
    @POST
    @Path("quick-add")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response quickAdd(@FormParam("title") String title, @FormParam("dueOn") String dueOn) {
        return switch (ParsedOneOffTask.parse(title, null, dueOn)) {
            case ParsedOneOffTask.Valid task -> created(task);
            case ParsedOneOffTask.Invalid invalid -> UiResponse.response(Status.BAD_REQUEST, UiRoute.BoardPage,
                BoardVM.of(boardHandler.board(), null, new QuickAddError(dueOn, String.join(" ", invalid.errors().values()))));
        };
    }

    /** The Tick checkbox, ticked. An unknown id is rejected ({@link RejectedExceptionMapper}). */
    @POST
    @Path("{id}/tick")
    public UiResponse tick(@PathParam("id") long id) {
        cardHandler.tick(id);
        return UiResponse.of(UiRoute.BoardPage, BoardVM.of(boardHandler.board()));
    }

    /** The Tick checkbox, unticked. An unknown id is rejected ({@link RejectedExceptionMapper}). */
    @POST
    @Path("{id}/untick")
    public UiResponse untick(@PathParam("id") long id) {
        cardHandler.untick(id);
        return UiResponse.of(UiRoute.BoardPage, BoardVM.of(boardHandler.board()));
    }

    /**
     * A Move (spec §2.5): {@code target} is an ISO day or {@code future}; {@code before} is the id of the Card to
     * land above, blank or absent for the bottom. A rejected Move answers 409 ({@link RejectedExceptionMapper}).
     */
    @POST
    @Path("{id}/move")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public UiResponse move(@PathParam("id") long id, @FormParam("target") String target, @FormParam("before") String before) {
        cardHandler.move(id, target, cardId(before));
        return UiResponse.of(UiRoute.BoardPage, BoardVM.of(boardHandler.board()));
    }

    /** A card id, or {@code null} when blank or not a number (the Move then goes to the bottom). */
    private static Long cardId(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String orEmpty(String value) {
        return Objects.requireNonNullElse(value, "");
    }

    private Response created(ParsedOneOffTask.Valid task) {
        cardHandler.createOneOff(task.title(), task.notes(), task.dueOn());
        return UiResponse.response(Status.OK, UiRoute.BoardPage, BoardVM.of(boardHandler.board()));
    }
}
