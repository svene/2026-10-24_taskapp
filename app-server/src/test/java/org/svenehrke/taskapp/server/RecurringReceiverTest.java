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
 * Weekly, Monthly and After-done Recurring Tasks from the New Task modal, their Occurrences, and the modal's Schedule preview,
 * with the Clock on Sun 25 Oct 2026.
 */
@QuarkusTest
class RecurringReceiverTest {

    private static final String WATER = """
        {"title": "Water the plants", "recurring": true}""";

    private static final String RENT = """
        {"title": "Pay the rent", "recurring": true}""";

    private static final String FILTER = """
        {"title": "Change the water filter", "recurring": true}""";

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
              "monthDay": "",
              "startsOn": "2026-10-25",
              "afterAmount": "",
              "afterUnit": "",
              "errors": {"weekdays": "Choose at least one weekday."},
              "preview": {"dates": [], "message": "Choose at least one weekday."}
            }
            """);
        assertThatJson(json).inPath("$.vm.future.nearest").isArray().isEmpty();
    }

    /** The example of the Monthly slice: 31 Oct in the span (days[6]), 30 Nov beyond it in Future. */
    @Test
    void a_monthly_last_day_task_shows_this_months_end_in_the_span_and_the_next_in_future() {
        var json = jsonBody(createMonthly("MONTHLY_LAST", null), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("$.vm.days[*].cards").isEqualTo("""
            [[], [], [], [], [], [], [%1$s], [], [], [], [], [], [], []]
            """.formatted(RENT));
        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.future.nearest").isEqualTo("""
            [{"title": "Pay the rent", "dateLabel": "30 Nov", "recurring": true}]
            """);
    }

    @Test
    void a_monthly_on_day_task_is_produced_on_that_day_and_described_in_its_occurrences() {
        var board = jsonBody(createMonthly("MONTHLY_DAY", "3"), 200);

        assertThatJson(board).when(IGNORING_EXTRA_FIELDS).inPath("$.vm.days[*].cards").isEqualTo("""
            [[], [], [], [], [], [], [], [], [], [%1$s], [], [], [], []]
            """.formatted(RENT));
        long id = occurrenceOn(board, 9);
        var json = jsonBody(given().when().get("/cards/" + id + "/edit"), 200);
        assertThatJson(json).inPath("vm.editOccurrence.schedule").isString()
            .isEqualTo("Every month on day 3, from 25 Oct 2026");
    }

    @Test
    void a_monthly_on_day_beyond_28_reopens_the_modal_with_its_message() {
        var json = jsonBody(createMonthly("MONTHLY_DAY", "31"), 400);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.newCard").isEqualTo("""
            {
              "kind": "MONTHLY_DAY",
              "monthDay": "31",
              "errors": {"monthDay": "Day of the month needs a whole number from 1 to 28."}
            }
            """);
    }

    @Test
    void the_preview_shows_the_next_five_dates_of_the_schedule() {
        var json = jsonBody(given().queryParam("kind", "MONTHLY_LAST").queryParam("startsOn", "2026-10-25")
            .when().get("/schedules/preview"), 200);

        assertThatJson(json).isEqualTo("""
            {
              "route": "SchedulePreview",
              "vm": {
                "dates": ["Sat 31 Oct 2026", "Mon 30 Nov 2026", "Thu 31 Dec 2026", "Sun 31 Jan 2027", "Sun 28 Feb 2027"],
                "message": null
              }
            }
            """);
    }

    @Test
    void the_preview_of_a_weekly_schedule_reads_its_weekdays() {
        var json = jsonBody(given().queryParam("kind", "WEEKLY").queryParam("weekdays", "1", "4")
            .queryParam("everyWeeks", "2").queryParam("startsOn", "2026-10-25").when().get("/schedules/preview"), 200);

        assertThatJson(json).inPath("vm.dates").isEqualTo("""
            ["Mon 2 Nov 2026", "Thu 5 Nov 2026", "Mon 16 Nov 2026", "Thu 19 Nov 2026", "Mon 30 Nov 2026"]
            """);
    }

    @Test
    void the_preview_of_incomplete_fields_says_what_is_missing() {
        var json = jsonBody(given().queryParam("kind", "MONTHLY_DAY").queryParam("monthDay", "29")
            .queryParam("startsOn", "").when().get("/schedules/preview"), 200);

        assertThatJson(json).inPath("vm").isEqualTo("""
            {
              "dates": [],
              "message": "Day of the month needs a whole number from 1 to 28. Start date is required."
            }
            """);
    }

    @Test
    void an_after_done_task_is_due_on_its_start_date_and_described_in_its_occurrence() {
        var board = jsonBody(createAfterDone("3", "WEEKS"), 200);

        assertThatJson(board).when(IGNORING_EXTRA_FIELDS).inPath("$.vm.days[*].cards").isEqualTo("""
            [[%s], [], [], [], [], [], [], [], [], [], [], [], [], []]
            """.formatted(FILTER));
        assertThatJson(board).inPath("$.vm.future.nearest").isArray().isEmpty();
        var json = jsonBody(given().when().get("/cards/" + occurrenceOn(board, 0) + "/edit"), 200);
        assertThatJson(json).inPath("vm.editOccurrence.schedule").isString()
            .isEqualTo("Every 3 weeks after done, from 25 Oct 2026");
    }

    /** The example of the After-done slice: due 25 Oct, done on 27 Oct, so the next is due 3 weeks later on 17 Nov. */
    @Test
    void ticking_an_after_done_occurrence_produces_the_next_one_counted_from_the_day_it_is_done() {
        long id = occurrenceOn(jsonBody(createAfterDone("3", "WEEKS"), 200), 0);
        clock.setToday(LocalDate.of(2026, 10, 27));

        var json = jsonBody(given().when().post("/cards/" + id + "/tick"), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.future.nearest").isEqualTo("""
            [{"title": "Change the water filter", "dateLabel": "17 Nov", "recurring": true}]
            """);
    }

    @Test
    void ticking_an_after_done_occurrence_again_produces_no_second_successor() {
        long id = occurrenceOn(jsonBody(createAfterDone("1", "DAYS"), 200), 0);
        jsonBody(given().when().post("/cards/" + id + "/tick"), 200);
        jsonBody(given().when().post("/cards/" + id + "/untick"), 200);

        var json = jsonBody(given().when().post("/cards/" + id + "/tick"), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("$.vm.days[*].cards").isEqualTo("""
            [[{"title": "Change the water filter", "done": true}], [%s], [], [], [], [], [], [], [], [], [], [], [], []]
            """.formatted(FILTER));
    }

    /** Dragged from today to tomorrow, then ticked off today: the next one is due the day after, not next to it. */
    @Test
    void ticking_an_after_done_occurrence_early_after_a_move_produces_the_next_one_after_the_moved_card() {
        long id = occurrenceOn(jsonBody(createAfterDone("1", "DAYS"), 200), 0);
        jsonBody(given().formParam("target", "2026-10-26").when().post("/cards/" + id + "/move"), 200);

        var json = jsonBody(given().when().post("/cards/" + id + "/tick"), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("$.vm.days[*].cards").isEqualTo("""
            [[], [{"title": "Change the water filter", "done": true}], [%s], [], [], [], [], [], [], [], [], [], [], []]
            """.formatted(FILTER));
    }

    /** The same in one save of the Occurrence modal: the move happens first, so the successor follows the new date. */
    @Test
    void saving_an_after_done_occurrence_as_moved_and_done_produces_the_next_one_after_the_new_date() {
        long id = occurrenceOn(jsonBody(createAfterDone("1", "DAYS"), 200), 0);

        var json = jsonBody(given().formParam("dueOn", "2026-10-26").formParam("done", "on")
            .when().post("/cards/" + id + "/occurrence"), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("$.vm.days[*].cards").isEqualTo("""
            [[], [{"title": "Change the water filter", "done": true}], [%s], [], [], [], [], [], [], [], [], [], [], []]
            """.formatted(FILTER));
    }

    @Test
    void an_after_done_task_without_a_whole_number_reopens_the_modal_with_its_message() {
        var json = jsonBody(createAfterDone("0", "WEEKS"), 400);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.newCard").isEqualTo("""
            {
              "repeats": "afterDone",
              "afterAmount": "0",
              "afterUnit": "WEEKS",
              "errors": {"afterAmount": "Every N days or weeks needs a whole number of at least 1."}
            }
            """);
    }

    @Test
    void the_preview_of_an_after_done_schedule_shows_its_start_date_and_how_the_rest_follow() {
        var json = jsonBody(given().queryParam("repeats", "afterDone").queryParam("kind", "WEEKLY")
            .queryParam("afterAmount", "1").queryParam("afterUnit", "DAYS").queryParam("startsOn", "2026-10-25")
            .when().get("/schedules/preview"), 200);

        assertThatJson(json).inPath("vm").isEqualTo("""
            {
              "dates": ["Sun 25 Oct 2026"],
              "message": "then 1 day after each time it is done"
            }
            """);
    }

    @Test
    void edit_on_an_occurrence_opens_its_modal_with_the_recurring_task_read_only() {
        long id = occurrenceOn(jsonBody(createWeekly("2026-10-25", "1", "4"), 200), 1);

        var json = jsonBody(given().when().get("/cards/" + id + "/edit"), 200);

        assertThatJson(json).inPath("vm.editOccurrence").isEqualTo("""
            {
              "id": %d,
              "recurringTaskId": "${json-unit.any-number}",
              "title": "Water the plants",
              "notes": "from the rain barrel",
              "schedule": "Every week on Mon, Thu, from 25 Oct 2026",
              "stoppedLabel": null,
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

    private static Response createMonthly(String kind, String monthDay) {
        return given().formParam("repeats", "calendar").formParam("title", "Pay the rent").formParam("notes", "")
            .formParam("dueOn", "2026-10-25").formParam("kind", kind).formParam("everyWeeks", "1")
            .formParam("monthDay", monthDay == null ? "" : monthDay).formParam("startsOn", "2026-10-25")
            .when().post("/cards");
    }

    private static Response createAfterDone(String afterAmount, String afterUnit) {
        return given().formParam("repeats", "afterDone").formParam("title", "Change the water filter")
            .formParam("notes", "").formParam("dueOn", "2026-10-25").formParam("kind", "WEEKLY")
            .formParam("everyWeeks", "1").formParam("startsOn", "2026-10-25").formParam("afterAmount", afterAmount)
            .formParam("afterUnit", afterUnit).when().post("/cards");
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
