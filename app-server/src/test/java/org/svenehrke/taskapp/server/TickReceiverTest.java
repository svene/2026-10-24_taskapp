package org.svenehrke.taskapp.server;

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS;
import static org.svenehrke.taskapp.server.JsonResponses.jsonBody;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Ticking Cards off and on, Done and Overdue; the Clock starts on Sun 25 Oct 2026 (day Column 1 is Mon 26 Oct). */
@QuarkusTest
class TickReceiverTest {

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
    void tick_strikes_the_card_through_and_untick_makes_it_open_again() {
        long id = quickAdd("Water the plants", "2026-10-26").getLong("vm.days[1].cards[0].taskId");

        var ticked = jsonBody(given().when().post("/cards/" + id + "/tick"), 200);

        assertThatJson(ticked).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[1].cards").isEqualTo("""
            [{"title": "Water the plants", "done": true, "overdue": false}]
            """);

        var unticked = jsonBody(given().when().post("/cards/" + id + "/untick"), 200);

        assertThatJson(unticked).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[1].cards").isEqualTo("""
            [{"title": "Water the plants", "done": false, "overdue": false}]
            """);
    }

    @Test
    void an_overdue_card_shows_in_todays_box_and_stays_there_struck_through_until_the_end_of_its_done_day() {
        clock.setToday(LocalDate.of(2026, 10, 20));
        long id = quickAdd("Pay the bill", "2026-10-20").getLong("vm.days[0].cards[0].taskId");
        clock.reset();

        var overdue = jsonBody(given().when().get("/board"), 200);

        assertThatJson(overdue).inPath("vm.days[0].overdue").isEqualTo("""
            [{"id": "card-%d", "taskId": %d, "title": "Pay the bill", "dateLabel": "20 Oct", "done": false, "overdue": true,
              "originalLabel": null, "moves": {"toToday": "2026-10-25", "plusDay": "2026-10-26", "plusWeek": "2026-11-01"}, "editable": true}]
            """.formatted(id, id));
        assertThatJson(overdue).inPath("$.vm.days[1:].overdue[*]").isArray().isEmpty();

        var ticked = jsonBody(given().when().post("/cards/" + id + "/tick"), 200);

        assertThatJson(ticked).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[0].overdue").isEqualTo("""
            [{"title": "Pay the bill", "dateLabel": "20 Oct", "done": true, "overdue": false}]
            """);

        clock.setToday(LocalDate.of(2026, 10, 26));
        var nextDay = jsonBody(given().when().get("/board"), 200);

        assertThatJson(nextDay).inPath("$.vm.days[*].overdue[*]").isArray().isEmpty();
        assertThatJson(nextDay).inPath("$.vm.days[*].cards[*]").isArray().isEmpty();
    }

    @Test
    void ticking_an_unknown_card_re_renders_the_board_with_a_notice() {
        quickAdd("Water the plants", "2026-10-26");

        var json = jsonBody(given().when().post("/cards/999999/tick"), 409);

        assertThatJson(json).inPath("route").isString().isEqualTo("BoardPage");
        assertThatJson(json).inPath("vm.notice").isString().isEqualTo("That task does not exist (any more).");
        assertThatJson(json).inPath("$.vm.days[*].cards[*].title").isEqualTo("""
            ["Water the plants"]
            """);
    }

    private static JsonPath quickAdd(String title, String dueOn) {
        return JsonPath.from(jsonBody(
            given().formParam("title", title).formParam("dueOn", dueOn).when().post("/cards/quick-add"), 200));
    }
}
