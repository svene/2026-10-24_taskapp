package org.svenehrke.taskapp.server;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** With the Clock on Sun 25 Oct 2026 ({@link MutableClock#DEFAULT_TODAY}). */
@QuarkusTest
class BoardReceiverTest {

    @Test
    void shell_loads_the_board_into_app() {
        given()
            .when().get("/")
            .then().statusCode(200)
            .contentType("text/html")
            .body(containsString("id=\"app\" hx-get=\"/board\""));
    }

    @Test
    void board_has_fourteen_day_columns_from_today_then_future() {
        given()
            .when().get("/board")
            .then().statusCode(200)
            .contentType("application/json")
            .body("route", equalTo("BoardPage"))
            .body("vm.days", hasSize(14))
            .body("vm.days.date", contains(
                "2026-10-25", "2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29", "2026-10-30", "2026-10-31",
                "2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06", "2026-11-07"))
            .body("vm.days.weekday", contains(
                "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"))
            .body("vm.days[0].dayMonth", equalTo("25 Oct"))
            .body("vm.days[13].dayMonth", equalTo("7 Nov"))
            .body("vm.days.today", contains(
                true, false, false, false, false, false, false, false, false, false, false, false, false, false))
            .body("vm.days.weekend", contains(
                true, false, false, false, false, false, true, true, false, false, false, false, false, true))
            .body("vm.future.fromLabel", equalTo("from 8 Nov"));
    }
}
