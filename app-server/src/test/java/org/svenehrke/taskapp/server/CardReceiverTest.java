package org.svenehrke.taskapp.server;

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS;
import static org.svenehrke.taskapp.server.JsonResponses.jsonBody;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Creating One-off Tasks, with the Clock on Sun 25 Oct 2026: day Column 1 is Mon 26 Oct. */
@QuarkusTest
class CardReceiverTest {

    @Inject
    AgroalDataSource dataSource;

    @BeforeEach
    void emptyBoard() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from card");
        }
    }

    @Test
    void quick_add_puts_the_card_at_the_bottom_of_its_column() {
        jsonBody(quickAdd("Water the plants", "2026-10-26"), 200);

        var json = jsonBody(quickAdd("Buy stamps", "2026-10-26"), 200);

        assertThatJson(json).inPath("route").isString().isEqualTo("BoardPage");
        assertThatJson(json).inPath("vm.days[1]").isEqualTo("""
            {
              "id": "col-2026-10-26",
              "date": "2026-10-26",
              "weekday": "Mon",
              "dayMonth": "26 Oct",
              "today": false,
              "weekend": false,
              "overdue": [],
              "cards": [
                {"id": "${json-unit.any-string}", "taskId": "${json-unit.any-number}", "title": "Water the plants", "dateLabel": null, "done": false, "overdue": false,
                 "originalLabel": null, "moves": {"toToday": null, "plusDay": "2026-10-27", "plusWeek": "2026-11-02"}},
                {"id": "${json-unit.any-string}", "taskId": "${json-unit.any-number}", "title": "Buy stamps",       "dateLabel": null, "done": false, "overdue": false,
                 "originalLabel": null, "moves": {"toToday": null, "plusDay": "2026-10-27", "plusWeek": "2026-11-02"}}
              ],
              "quickAddError": null
            }
            """);
        assertThatJson(json).inPath("$.vm.days[*].cards[*].title").isEqualTo("""
            ["Water the plants", "Buy stamps"]
            """);
        assertThatJson(json).inPath("vm.newCard").isNull();
    }

    @Test
    void quick_add_with_a_blank_title_shows_the_message_in_its_column() {
        var json = jsonBody(quickAdd("   ", "2026-10-26"), 400);

        assertThatJson(json).inPath("route").isString().isEqualTo("BoardPage");
        assertThatJson(json).inPath("$.vm.days[*].quickAddError").isEqualTo("""
            [null, "Title must not be blank.", null, null, null, null, null, null, null, null, null, null, null, null]
            """);
        assertThatJson(json).inPath("$.vm.days[*].cards[*]").isArray().isEmpty();
    }

    @Test
    void plus_opens_the_modal_with_the_due_date_preset() {
        var json = jsonBody(given().queryParam("dueOn", "2026-10-28").when().get("/cards/new"), 200);

        assertThatJson(json).inPath("vm.newCard").isEqualTo("""
            {
              "title": "",
              "notes": "",
              "dueOn": "2026-10-28",
              "errors": {}
            }
            """);
    }

    @Test
    void the_modal_creates_a_card_with_notes() {
        var json = jsonBody(
            given().formParam("title", "Book flights").formParam("notes", "window seat").formParam("dueOn", "2026-10-27")
                .when().post("/cards"),
            200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[2]").isEqualTo("""
            {
              "date": "2026-10-27",
              "cards": [{"title": "Book flights"}]
            }
            """);
        assertThatJson(json).inPath("vm.newCard").isNull();
    }

    @Test
    void the_modal_with_a_blank_title_reopens_with_its_message_and_input() {
        var json = jsonBody(
            given().formParam("title", "").formParam("notes", "window seat").formParam("dueOn", "2026-10-27")
                .when().post("/cards"),
            400);

        assertThatJson(json).inPath("vm.newCard").isEqualTo("""
            {
              "title": "",
              "notes": "window seat",
              "dueOn": "2026-10-27",
              "errors": {"title": "Title must not be blank."}
            }
            """);
        assertThatJson(json).inPath("$.vm.days[*].cards[*]").isArray().isEmpty();
    }

    @Test
    void cards_after_the_span_show_in_future_five_then_more() {
        for (var dueOn : new String[] {"2026-11-30", "2026-11-09", "2026-11-12", "2026-11-08", "2026-11-20", "2026-11-09"}) {
            jsonBody(quickAdd("due " + dueOn, dueOn), 200);
        }

        var json = jsonBody(given().when().get("/board"), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.future").isEqualTo("""
            {
              "from": "2026-11-08",
              "fromLabel": "from 8 Nov",
              "nearest": [
                {"title": "due 2026-11-08", "dateLabel": "8 Nov"},
                {"title": "due 2026-11-09", "dateLabel": "9 Nov"},
                {"title": "due 2026-11-09", "dateLabel": "9 Nov"},
                {"title": "due 2026-11-12", "dateLabel": "12 Nov"},
                {"title": "due 2026-11-20", "dateLabel": "20 Nov"}
              ],
              "more": [
                {"title": "due 2026-11-30", "dateLabel": "30 Nov"}
              ]
            }
            """);
        assertThatJson(json).inPath("$.vm.days[*].cards[*]").isArray().isEmpty();
    }

    private static Response quickAdd(String title, String dueOn) {
        return given().formParam("title", title).formParam("dueOn", dueOn).when().post("/cards/quick-add");
    }
}
