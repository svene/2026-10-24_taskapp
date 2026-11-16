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

/** Editing a One-off Task in the Edit modal, with the Clock on Sun 25 Oct 2026 (day Column 0 is Today). */
@QuarkusTest
class EditReceiverTest {

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
    void edit_opens_the_modal_with_the_stored_values_and_today_as_the_earliest_due_date() {
        long id = create("Book flights", "window seat", "2026-10-27");

        var json = jsonBody(given().when().get("/cards/" + id + "/edit"), 200);

        assertThatJson(json).inPath("vm.editCard").isEqualTo("""
            {
              "id": %d,
              "title": "Book flights",
              "notes": "window seat",
              "dueOn": "2026-10-27",
              "minDueOn": "2026-10-25",
              "errors": {}
            }
            """.formatted(id));
        assertThatJson(json).inPath("vm.newCard").isNull();
    }

    @Test
    void an_overdue_card_can_keep_its_day_in_the_modal() {
        clock.setToday(LocalDate.of(2026, 10, 20));
        long id = create("Pay the bill", null, "2026-10-20");
        clock.reset();

        var json = jsonBody(given().when().get("/cards/" + id + "/edit"), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.editCard").isEqualTo("""
            {"notes": "", "dueOn": "2026-10-20", "minDueOn": "2026-10-20"}
            """);
    }

    @Test
    void save_changes_title_and_notes_and_closes_the_modal() {
        long id = create("Book flights", "window seat", "2026-10-27");

        var json = jsonBody(save(id, "Book the flights", "aisle seat", "2026-10-27"), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[2].cards").isEqualTo("""
            [{"title": "Book the flights", "originalLabel": null, "recurring": false}]
            """);
        assertThatJson(json).inPath("vm.editCard").isNull();
        assertThatJson(jsonBody(given().when().get("/cards/" + id + "/edit"), 200)).inPath("vm.editCard.notes")
            .isString().isEqualTo("aisle seat");
    }

    @Test
    void save_with_a_new_due_date_moves_the_card_to_the_bottom_of_that_day_with_its_original_date() {
        create("already there", null, "2026-10-29");
        long id = create("Book flights", null, "2026-10-27");

        var json = jsonBody(save(id, "Book flights", "", "2026-10-29"), 200);

        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[4].cards").isEqualTo("""
            [{"title": "already there", "originalLabel": null},
             {"title": "Book flights",  "originalLabel": "27 Oct"}]
            """);
        assertThatJson(json).inPath("vm.days[2].cards").isArray().isEmpty();
    }

    @Test
    void save_with_a_blank_title_reopens_the_modal_with_its_message_and_input() {
        long id = create("Book flights", null, "2026-10-27");

        var json = jsonBody(save(id, " ", "window seat", "2026-10-28"), 400);

        assertThatJson(json).inPath("vm.editCard").isEqualTo("""
            {
              "id": %d,
              "title": " ",
              "notes": "window seat",
              "dueOn": "2026-10-28",
              "minDueOn": "2026-10-25",
              "errors": {"title": "Title must not be blank."}
            }
            """.formatted(id));
        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[2].cards").isEqualTo("""
            [{"title": "Book flights"}]
            """);
    }

    @Test
    void save_to_a_past_day_is_rejected_and_changes_nothing() {
        long id = create("Book flights", null, "2026-10-27");

        var json = jsonBody(save(id, "renamed", "", "2026-10-24"), 409);

        assertThatJson(json).inPath("vm.notice").isString().isEqualTo("A task cannot be moved to a past day.");
        assertThatJson(json).when(IGNORING_EXTRA_FIELDS).inPath("vm.days[2].cards").isEqualTo("""
            [{"title": "Book flights"}]
            """);
    }

    @Test
    void an_occurrence_is_not_saved_as_a_one_off_task() throws SQLException {
        execute("insert into recurring_task (id, title, starts_on, kind, after_amount, after_unit)"
            + " values (1, 'Water the plants', '2026-10-25', 'AFTER_DONE', 3, 'DAYS')");
        execute("insert into card (id, recurring_task_id, due_on, original_due_on, position)"
            + " values (1, 1, '2026-10-26', '2026-10-26', 0)");

        assertThatJson(jsonBody(save(1, "renamed", "", "2026-10-26"), 409)).inPath("vm.notice")
            .isString().isEqualTo("A recurring task's occurrence cannot be edited here.");
    }

    @Test
    void editing_a_card_that_no_longer_exists_is_rejected() {
        assertThatJson(jsonBody(given().when().get("/cards/42/edit"), 409)).inPath("vm.notice")
            .isString().isEqualTo("That task does not exist (any more).");
    }

    private static long create(String title, String notes, String dueOn) {
        var request = given().formParam("title", title).formParam("dueOn", dueOn);
        if (notes != null) request = request.formParam("notes", notes);
        var board = JsonPath.from(jsonBody(request.when().post("/cards"), 200));
        return board.getLong("vm.days.collect { it.cards + it.overdue }.flatten().find { it.title == '%s' }.taskId"
            .formatted(title));
    }

    private static Response save(long id, String title, String notes, String dueOn) {
        return given().formParam("title", title).formParam("notes", notes).formParam("dueOn", dueOn)
            .when().post("/cards/" + id);
    }

    private void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
