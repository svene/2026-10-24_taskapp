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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A Weekly Recurring Task from the New Task modal, and its Occurrences, with the Clock on Sun 25 Oct 2026. */
@QuarkusTest
class RecurringReceiverTest {

    private static final String WATER = """
        {"title": "Water the plants", "recurring": true}""";

    /** Mon + Thu from today: 26, 29 Oct and 2, 5 Nov in the span (days[1], [4], [8], [11]). */
    private static final String MON_THU_COLUMNS = """
        [[], [%1$s], [], [], [%1$s], [], [], [], [%1$s], [], [], [%1$s], [], []]
        """.formatted(WATER);

    @Inject
    AgroalDataSource dataSource;

    @Inject
    MutableClock clock;

    @BeforeEach
    void emptyBoard() throws SQLException {
        execute("delete from card");
        execute("delete from recurring_task");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void a_weekly_task_shows_its_occurrences_in_the_span_and_the_next_one_in_future() {
        var json = jsonBody(createWeekly("2026-10-25", "1", "4"), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("$.vm.days[*].cards").isEqualTo(MON_THU_COLUMNS);
        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.future.nearest").isEqualTo("""
            [{"title": "Water the plants", "dateLabel": "9 Nov", "recurring": true}]
            """);
        assertThatJson(json).inPath("vm.newCard").isNull();
    }

    @Test
    void loading_the_board_again_creates_no_duplicates() {
        jsonBody(createWeekly("2026-10-25", "1", "4"), 200);

        var json = jsonBody(given().when().get("/board"), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("$.vm.days[*].cards").isEqualTo(MON_THU_COLUMNS);
        assertThatJson(json).inPath("$.vm.future.nearest[*].title").isEqualTo("""
            ["Water the plants"]
            """);
    }

    @Test
    void a_past_start_date_backfills_overdue_occurrences() {
        var json = jsonBody(createWeekly("2026-10-15", "1", "4"), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[0].overdue").isEqualTo("""
            [
              {"title": "Water the plants", "dateLabel": "15 Oct", "overdue": true, "recurring": true},
              {"title": "Water the plants", "dateLabel": "19 Oct", "overdue": true, "recurring": true},
              {"title": "Water the plants", "dateLabel": "22 Oct", "overdue": true, "recurring": true}
            ]
            """);
    }

    @Test
    void the_modal_without_weekdays_reopens_with_its_message_and_input() {
        var json = jsonBody(given().formParam("repeats", "calendar").formParam("title", "Water the plants")
            .formParam("notes", "").formParam("dueOn", "2026-10-25").formParam("kind", "WEEKLY")
            .formParam("everyWeeks", "2").formParam("startsOn", "2026-10-25").when().post("/cards"), 400);

        assertThatJson(json).inPath("vm.newCard").isEqualTo("""
            {
              "repeats": "calendar",
              "title": "Water the plants",
              "notes": "",
              "dueOn": "2026-10-25",
              "kind": "WEEKLY",
              "weekdays": [],
              "everyWeeks": "2",
              "startsOn": "2026-10-25",
              "errors": {"weekdays": "Choose at least one weekday."}
            }
            """);
        assertThatJson(json).inPath("$.vm.future.nearest").isArray().isEmpty();
    }

    @Test
    void edit_on_an_occurrence_opens_its_modal_with_the_recurring_task_read_only() {
        long id = occurrenceOn(jsonBody(createWeekly("2026-10-25", "1", "4"), 200), 1);

        var json = jsonBody(given().when().get("/cards/" + id + "/edit"), 200);

        assertThatJson(json).inPath("vm.editOccurrence").isEqualTo("""
            {
              "id": %d,
              "title": "Water the plants",
              "notes": "from the rain barrel",
              "schedule": "Every week on Mon, Thu, from 25 Oct 2026",
              "dueOn": "2026-10-26",
              "minDueOn": "2026-10-25",
              "done": false,
              "errors": {}
            }
            """.formatted(id));
        assertThatJson(json).inPath("vm.editCard").isNull();
    }

    @Test
    void saving_an_occurrence_moves_and_ticks_it_without_reproducing_it() {
        long id = occurrenceOn(jsonBody(createWeekly("2026-10-25", "1", "4"), 200), 1);

        var json = jsonBody(given().formParam("dueOn", "2026-10-27").formParam("done", "on")
            .when().post("/cards/" + id + "/occurrence"), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[1].cards").isEqualTo("[]");
        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[2].cards").isEqualTo("""
            [{"taskId": %d, "title": "Water the plants", "done": true, "originalLabel": "26 Oct", "recurring": true}]
            """.formatted(id));
        assertThatJson(json).inPath("vm.editOccurrence").isNull();
    }

    @Test
    void saving_an_occurrence_without_a_due_date_reopens_its_modal() {
        long id = occurrenceOn(jsonBody(createWeekly("2026-10-25", "1", "4"), 200), 1);

        var json = jsonBody(given().formParam("dueOn", "").formParam("done", "on")
            .when().post("/cards/" + id + "/occurrence"), 400);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.editOccurrence").isEqualTo("""
            {"id": %d, "dueOn": "", "done": true, "errors": {"dueOn": "Due date is required."}}
            """.formatted(id));
    }

    @Test
    void moving_an_occurrence_to_a_past_day_is_rejected() {
        long id = occurrenceOn(jsonBody(createWeekly("2026-10-25", "1", "4"), 200), 1);

        var json = jsonBody(given().formParam("dueOn", "2026-10-24").when().post("/cards/" + id + "/occurrence"), 409);

        assertThatJson(json).inPath("vm.notice").isString().isEqualTo("A task cannot be moved to a past day.");
    }

    private static Response createWeekly(String startsOn, String... weekdays) {
        return given().formParam("repeats", "calendar").formParam("title", "Water the plants")
            .formParam("notes", "from the rain barrel").formParam("dueOn", "2026-10-25").formParam("kind", "WEEKLY")
            .formParam("weekdays", (Object[]) weekdays).formParam("everyWeeks", "1").formParam("startsOn", startsOn)
            .when().post("/cards");
    }

    private static long occurrenceOn(String board, int day) {
        return JsonPath.from(board).getLong("vm.days[%d].cards[0].taskId".formatted(day));
    }

    private void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
