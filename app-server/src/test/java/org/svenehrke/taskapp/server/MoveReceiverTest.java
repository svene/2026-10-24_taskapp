package org.svenehrke.taskapp.server;

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS;
import static org.svenehrke.taskapp.server.JsonResponses.jsonBody;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Moving Cards with the card buttons: each posts the absolute date the Board gave it. The Clock is on Sun 25 Oct 2026
 * (day Column 0 is Today, Column 7 is Sun 1 Nov).
 */
@QuarkusTest
class MoveReceiverTest {

    @Inject
    AgroalDataSource dataSource;

    @Inject
    MutableClock clock;

    @BeforeEach
    void emptyBoard() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from card");
        }
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void the_buttons_of_a_card_carry_absolute_dates() {
        var board = quickAdd("Water the plants", "2026-10-25");

        assertThatJson(board.prettify()).inPath("vm.days[0].cards[0].moves").isEqualTo("""
            {"toToday": null, "plusDay": "2026-10-26", "plusWeek": "2026-11-01"}
            """);
    }

    @Test
    void plus_one_week_moves_the_card_to_the_bottom_of_that_day_and_shows_its_original_due_date() {
        quickAdd("Buy stamps", "2026-11-01");
        var board = quickAdd("Water the plants", "2026-10-25");

        var json = jsonBody(move(board.getLong("vm.days[0].cards[0].taskId"), board.getString("vm.days[0].cards[0].moves.plusWeek")), 200);

        assertThatJson(json).inPath("$.vm.days[0].cards").isArray().isEmpty();
        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[7]").isEqualTo("""
            {
              "date": "2026-11-01",
              "cards": [
                {"title": "Buy stamps",       "originalLabel": null},
                {"title": "Water the plants", "originalLabel": "25 Oct", "moves": {"toToday": null, "plusDay": "2026-11-02", "plusWeek": "2026-11-08"}}
              ]
            }
            """);
    }

    @Test
    void plus_one_day_and_back_clears_the_original_due_date() {
        var board = quickAdd("Water the plants", "2026-10-25");
        long id = board.getLong("vm.days[0].cards[0].taskId");

        var moved = JsonPath.from(jsonBody(move(id, board.getString("vm.days[0].cards[0].moves.plusDay")), 200));

        assertThatJson(moved.prettify()).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[1].cards").isEqualTo("""
            [{"title": "Water the plants", "originalLabel": "25 Oct"}]
            """);

        var back = jsonBody(move(id, "2026-10-25"), 200);

        assertThatJson(back).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[0].cards").isEqualTo("""
            [{"title": "Water the plants", "originalLabel": null}]
            """);
    }

    @Test
    void to_today_moves_an_overdue_card_out_of_the_overdue_box() {
        clock.setToday(LocalDate.of(2026, 10, 20));
        quickAdd("Pay the bill", "2026-10-20");
        clock.reset();
        var board = JsonPath.from(jsonBody(given().when().get("/board"), 200));

        assertThatJson(board.prettify()).inPath("vm.days[0].overdue[0].moves").isEqualTo("""
            {"toToday": "2026-10-25", "plusDay": "2026-10-26", "plusWeek": "2026-11-01"}
            """);

        var json = jsonBody(move(board.getLong("vm.days[0].overdue[0].taskId"), board.getString("vm.days[0].overdue[0].moves.toToday")), 200);

        assertThatJson(json).inPath("vm.days[0].overdue").isArray().isEmpty();
        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[0].cards").isEqualTo("""
            [{"title": "Pay the bill", "overdue": false, "originalLabel": "20 Oct"}]
            """);
    }

    @Test
    void a_done_card_has_no_buttons() {
        long id = quickAdd("Water the plants", "2026-10-25").getLong("vm.days[0].cards[0].taskId");

        var json = jsonBody(given().when().post("/cards/" + id + "/tick"), 200);

        assertThatJson(json).inPath("vm.days[0].cards[0].moves").isNull();
    }

    @Test
    void a_move_to_a_past_day_is_rejected_with_a_notice() {
        long id = quickAdd("Water the plants", "2026-10-25").getLong("vm.days[0].cards[0].taskId");

        var json = jsonBody(move(id, "2026-10-24"), 409);

        assertThatJson(json).inPath("vm.notice").isString().isEqualTo("A task cannot be moved to a past day.");
        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[0].cards").isEqualTo("""
            [{"title": "Water the plants", "originalLabel": null}]
            """);
    }

    @Test
    void a_move_to_future_lands_on_the_day_after_the_span_before_the_given_card() {
        quickAdd("already there", "2026-11-08");
        var board = quickAdd("Water the plants", "2026-10-25");
        long first = board.getLong("vm.future.nearest[0].taskId");

        var json = jsonBody(given().formParam("target", "future").formParam("before", first)
            .when().post("/cards/" + board.getLong("vm.days[0].cards[0].taskId") + "/move"), 200);

        assertThatJson(json).inPath("$.vm.future.nearest[*].title").isEqualTo("""
            ["Water the plants", "already there"]
            """);
    }

    private static Response move(long id, String target) {
        return given().formParam("target", target).when().post("/cards/" + id + "/move");
    }

    private static JsonPath quickAdd(String title, String dueOn) {
        return JsonPath.from(jsonBody(
            given().formParam("title", title).formParam("dueOn", dueOn).when().post("/cards/quick-add"), 200));
    }
}
