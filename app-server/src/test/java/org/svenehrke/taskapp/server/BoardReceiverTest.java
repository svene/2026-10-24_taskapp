package org.svenehrke.taskapp.server;

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.svenehrke.taskapp.server.JsonResponses.jsonBody;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** With the Clock on Sun 25 Oct 2026 ({@link MutableClock#DEFAULT_TODAY}). */
@QuarkusTest
class BoardReceiverTest {

    @Test
    void shell_loads_the_board_into_app() {
        var response = given().when().get("/");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).startsWith("text/html");
        assertThat(response.asString()).contains("id=\"app\" hx-get=\"/board\"");
    }

    @Test
    void board_has_fourteen_day_columns_from_today_then_future() {
        var json = jsonBody(given().when().get("/board"), 200);

        // other tests may have left Cards in the shared database; this one only looks at the Columns
        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).isEqualTo("""
            {
              "route": "BoardPage",
              "vm": {
                "days": [
                  {"id": "col-2026-10-25", "date": "2026-10-25", "weekday": "Sun", "dayMonth": "25 Oct", "today": true,  "weekend": true},
                  {"id": "col-2026-10-26", "date": "2026-10-26", "weekday": "Mon", "dayMonth": "26 Oct", "today": false, "weekend": false},
                  {"id": "col-2026-10-27", "date": "2026-10-27", "weekday": "Tue", "dayMonth": "27 Oct", "today": false, "weekend": false},
                  {"id": "col-2026-10-28", "date": "2026-10-28", "weekday": "Wed", "dayMonth": "28 Oct", "today": false, "weekend": false},
                  {"id": "col-2026-10-29", "date": "2026-10-29", "weekday": "Thu", "dayMonth": "29 Oct", "today": false, "weekend": false},
                  {"id": "col-2026-10-30", "date": "2026-10-30", "weekday": "Fri", "dayMonth": "30 Oct", "today": false, "weekend": false},
                  {"id": "col-2026-10-31", "date": "2026-10-31", "weekday": "Sat", "dayMonth": "31 Oct", "today": false, "weekend": true},
                  {"id": "col-2026-11-01", "date": "2026-11-01", "weekday": "Sun", "dayMonth": "1 Nov",  "today": false, "weekend": true},
                  {"id": "col-2026-11-02", "date": "2026-11-02", "weekday": "Mon", "dayMonth": "2 Nov",  "today": false, "weekend": false},
                  {"id": "col-2026-11-03", "date": "2026-11-03", "weekday": "Tue", "dayMonth": "3 Nov",  "today": false, "weekend": false},
                  {"id": "col-2026-11-04", "date": "2026-11-04", "weekday": "Wed", "dayMonth": "4 Nov",  "today": false, "weekend": false},
                  {"id": "col-2026-11-05", "date": "2026-11-05", "weekday": "Thu", "dayMonth": "5 Nov",  "today": false, "weekend": false},
                  {"id": "col-2026-11-06", "date": "2026-11-06", "weekday": "Fri", "dayMonth": "6 Nov",  "today": false, "weekend": false},
                  {"id": "col-2026-11-07", "date": "2026-11-07", "weekday": "Sat", "dayMonth": "7 Nov",  "today": false, "weekend": true}
                ],
                "future": {"from": "2026-11-08", "fromLabel": "from 8 Nov"},
                "newCard": null
              }
            }
            """);
    }
}
