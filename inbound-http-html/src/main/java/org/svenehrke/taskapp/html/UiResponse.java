package org.svenehrke.taskapp.html;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The JSON envelope every view endpoint returns: {@code {"route":"BoardPage","vm":{…}}}. The browser-side
 * {@code hono} htmx extension ({@code hx-hono.ts}) renders the template named by {@code route} on {@code vm}
 * and hands the HTML to htmx for the swap.
 */
public record UiResponse(String route, Object vm) {

    public static UiResponse of(UiRoute route, Object vm) {
        return new UiResponse(route.name(), vm);
    }

    public static Response response(Response.Status status, UiRoute route, Object vm) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(of(route, vm)).build();
    }
}
