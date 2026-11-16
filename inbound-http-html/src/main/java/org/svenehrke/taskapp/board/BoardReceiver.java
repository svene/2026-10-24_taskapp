package org.svenehrke.taskapp.board;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.svenehrke.taskapp.html.PageShell;
import org.svenehrke.taskapp.html.UiResponse;
import org.svenehrke.taskapp.html.UiRoute;

@RunOnVirtualThread
@Path("/")
public class BoardReceiver {

    @Inject
    BoardHandler boardHandler;

    /** The static page shell; its {@code #app} element loads {@link #board()} and renders it in the browser. */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String shell() {
        return PageShell.read("/shells/board.html");
    }

    @GET
    @Path("board")
    @Produces(MediaType.APPLICATION_JSON)
    public UiResponse board() {
        return UiResponse.of(UiRoute.BoardPage, BoardVM.of(boardHandler.board()));
    }
}
