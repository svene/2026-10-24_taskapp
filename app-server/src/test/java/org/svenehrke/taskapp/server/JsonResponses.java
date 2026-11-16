package org.svenehrke.taskapp.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;

/** Receiver responses in tests: check status and content type, then hand the body to {@code assertThatJson}. */
final class JsonResponses {

    private JsonResponses() {}

    /** The JSON body of {@code response}, after asserting it has {@code expectedStatus} and is JSON. */
    static String jsonBody(Response response, int expectedStatus) {
        var body = response.asString();
        assertThat(response.statusCode()).as("status of a response with body %s", body).isEqualTo(expectedStatus);
        assertThat(response.contentType()).startsWith("application/json");
        return body;
    }
}
