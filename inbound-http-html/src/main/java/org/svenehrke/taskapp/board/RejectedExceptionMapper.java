package org.svenehrke.taskapp.board;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.svenehrke.taskapp.html.UiResponse;
import org.svenehrke.taskapp.html.UiRoute;
import org.svenehrke.taskapp.shared.RejectedException;

/** A command the domain rejected: the Board, re-read after the rollback, with the reason as a short notice (409). */
@Provider
public class RejectedExceptionMapper implements ExceptionMapper<RejectedException> {

    @Inject
    BoardHandler boardHandler;

    @Override
    public Response toResponse(RejectedException rejected) {
        return UiResponse.response(Status.CONFLICT, UiRoute.BoardPage, BoardVM.rejected(boardHandler.board(), rejected.reason()));
    }
}
