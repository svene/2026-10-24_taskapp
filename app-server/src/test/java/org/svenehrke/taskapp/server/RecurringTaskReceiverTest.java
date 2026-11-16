package org.svenehrke.taskapp.server;

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS;
import static org.svenehrke.taskapp.server.JsonResponses.jsonBody;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Editing and Stopping a Recurring Task from the Occurrence modal, and Stale Occurrences, with the Clock on Sun
 * 25 Oct 2026 and "Water the plants" every Mon + Thu from today: 26, 29 Oct and 2, 5 Nov in the span (days[1], [4],
 * [8], [11]), 9 Nov in Future.
 */
@QuarkusTest
class RecurringTaskReceiverTest {

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
    void edit_recurring_task_on_an_occurrence_opens_the_modal_with_its_schedule() {
        long taskId = createMonThu();

        var json = jsonBody(given().when().get("/recurring-tasks/" + taskId + "/edit"), 200);

        assertThatJson(json).inPath("vm.editRecurring").isEqualTo("""
            {
              "id": %d,
              "repeats": "calendar",
              "title": "Water the plants",
              "notes": "from the rain barrel",
              "kind": "WEEKLY",
              "weekdays": [1, 4],
              "everyWeeks": "1",
              "monthDay": "25",
              "startsOn": "2026-10-25",
              "afterAmount": "1",
              "afterUnit": "WEEKS",
              "errors": {},
              "preview": {
                "dates": ["Mon 26 Oct 2026", "Thu 29 Oct 2026", "Mon 2 Nov 2026", "Thu 5 Nov 2026", "Mon 9 Nov 2026"],
                "message": null
              }
            }
            """.formatted(taskId));
        assertThatJson(json).inPath("vm.editOccurrence").isNull();
    }

    /** The example of the slice: Mon + Thu edited to Mon only. */
    @Test
    void editing_away_a_weekday_marks_its_occurrences_stale_and_keeps_the_others() {
        long taskId = createMonThu();

        var json = jsonBody(save(taskId, "Water the plants").formParam("weekdays", "1").when().post("/recurring-tasks/" + taskId), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("$.vm.days[*].cards").isEqualTo("""
            [[], [{"stale": false}], [], [], [{"stale": true}], [], [], [], [{"stale": false}], [], [], [{"stale": true}], [], []]
            """);
        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.future.nearest").isEqualTo("""
            [{"dateLabel": "9 Nov", "stale": false}]
            """);
        assertThatJson(json).inPath("vm.editRecurring").isNull();
    }

    @Test
    void an_occurrence_dragged_to_a_day_the_schedule_keeps_is_still_stale_when_its_own_date_is_edited_away() {
        long taskId = createMonThu();
        long thursday = JsonPath.from(board()).getLong("vm.days[4].cards[0].taskId");
        jsonBody(given().formParam("target", "2026-11-02").when().post("/cards/" + thursday + "/move"), 200);

        var json = jsonBody(save(taskId, "Water the plants").formParam("weekdays", "1").when().post("/recurring-tasks/" + taskId), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[8].cards").isEqualTo("""
            [{"stale": false, "originalLabel": null}, {"stale": true, "originalLabel": "29 Oct"}]
            """);
    }

    @Test
    void a_rename_shows_on_every_occurrence() {
        long taskId = createMonThu();

        var json = jsonBody(save(taskId, "Water the herbs").formParam("weekdays", "1", "4").when().post("/recurring-tasks/" + taskId), 200);

        assertThatJson(json).inPath("$.vm.days[*].cards[*].title").isEqualTo("""
            ["Water the herbs", "Water the herbs", "Water the herbs", "Water the herbs"]
            """);
        assertThatJson(json).inPath("$.vm.future.nearest[*].title").isEqualTo("""
            ["Water the herbs"]
            """);
    }

    @Test
    void an_edit_without_weekdays_reopens_the_modal_with_its_message_and_input() {
        long taskId = createMonThu();

        var json = jsonBody(save(taskId, "Water the herbs").when().post("/recurring-tasks/" + taskId), 400);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.editRecurring").isEqualTo("""
            {"id": %d, "repeats": "calendar", "title": "Water the herbs", "weekdays": [], "errors": {"weekdays": "Choose at least one weekday."}}
            """.formatted(taskId));
        assertThatJson(json).inPath("$.vm.days[*].cards[*].title").isEqualTo("""
            ["Water the plants", "Water the plants", "Water the plants", "Water the plants"]
            """);
    }

    @Test
    void stop_marks_the_open_occurrences_stale_moves_them_out_of_futures_nearest_and_says_so_in_the_occurrence_modal() {
        long taskId = createMonThu();

        var json = jsonBody(given().when().post("/recurring-tasks/" + taskId + "/stop"), 200);

        assertThatJson(json).inPath("$.vm.days[*].cards[*].stale").isEqualTo("""
            [true, true, true, true]
            """);
        assertThatJson(json).inPath("vm.future.nearest").isArray().isEmpty();
        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.future.more").isEqualTo("""
            [{"dateLabel": "9 Nov", "stale": true}]
            """);
        long occurrence = JsonPath.from(json).getLong("vm.days[1].cards[0].taskId");
        var modal = jsonBody(given().when().get("/cards/" + occurrence + "/edit"), 200);
        assertThatJson(modal).inPath("vm.editOccurrence.stoppedLabel").isString().isEqualTo("Stopped on 25 Oct 2026");
    }

    @Test
    void a_stopped_task_produces_no_new_occurrences_when_the_clock_advances() {
        long taskId = createMonThu();
        jsonBody(given().when().post("/recurring-tasks/" + taskId + "/stop"), 200);

        clock.setToday(LocalDate.of(2026, 11, 20));
        var json = board();

        assertThatJson(json).inPath("$.vm.days[*].cards[*]").isArray().isEmpty();
        assertThatJson(json).inPath("$.vm.future.more[*]").isArray().isEmpty();
        assertThatJson(json).inPath("$.vm.days[0].overdue[*].dateLabel").isEqualTo("""
            ["26 Oct", "29 Oct", "2 Nov", "5 Nov", "9 Nov"]
            """);
    }

    @Test
    void editing_a_stopped_task_is_rejected_with_a_notice() {
        long taskId = createMonThu();
        jsonBody(given().when().post("/recurring-tasks/" + taskId + "/stop"), 200);

        var json = jsonBody(save(taskId, "Water the herbs").formParam("weekdays", "1").when().post("/recurring-tasks/" + taskId), 409);

        assertThatJson(json).inPath("vm.notice").isString().isEqualTo("That recurring task is stopped.");
    }

    /** Creates the task and returns its id, read from its first Occurrence's modal. */
    private static long createMonThu() {
        var created = jsonBody(given().formParam("repeats", "calendar").formParam("title", "Water the plants")
            .formParam("notes", "from the rain barrel").formParam("dueOn", "2026-10-25").formParam("kind", "WEEKLY")
            .formParam("weekdays", "1", "4").formParam("everyWeeks", "1").formParam("startsOn", "2026-10-25")
            .when().post("/cards"), 200);
        long occurrence = JsonPath.from(created).getLong("vm.days[1].cards[0].taskId");
        return JsonPath.from(jsonBody(given().when().get("/cards/" + occurrence + "/edit"), 200))
            .getLong("vm.editOccurrence.recurringTaskId");
    }

    /** The modal's fields for a weekly Schedule from today, without weekdays. */
    private static RequestSpecification save(long taskId, String title) {
        return given().formParam("repeats", "calendar").formParam("title", title).formParam("notes", "from the rain barrel")
            .formParam("kind", "WEEKLY").formParam("everyWeeks", "1").formParam("startsOn", "2026-10-25");
    }

    private static String board() {
        Response response = given().when().get("/board");
        return jsonBody(response, 200);
    }

    private void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
