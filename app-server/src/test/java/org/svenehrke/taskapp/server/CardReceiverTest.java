package org.svenehrke.taskapp.server;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.nullValue;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
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
        quickAdd("Water the plants", "2026-10-26").then().statusCode(200);

        quickAdd("Buy stamps", "2026-10-26")
            .then().statusCode(200)
            .contentType("application/json")
            .body("route", equalTo("BoardPage"))
            .body("vm.days[1].date", equalTo("2026-10-26"))
            .body("vm.days[1].cards.title", contains("Water the plants", "Buy stamps"))
            .body("vm.days[1].quickAddError", nullValue())
            .body("vm.days[0].cards", empty())
            .body("vm.newCard", nullValue());
    }

    @Test
    void quick_add_with_a_blank_title_shows_the_message_in_its_column() {
        quickAdd("   ", "2026-10-26")
            .then().statusCode(400)
            .body("route", equalTo("BoardPage"))
            .body("vm.days[1].quickAddError", equalTo("Title must not be blank."))
            .body("vm.days[0].quickAddError", nullValue())
            .body("vm.days.cards.flatten()", empty());
    }

    @Test
    void plus_opens_the_modal_with_the_due_date_preset() {
        given().queryParam("dueOn", "2026-10-28")
            .when().get("/cards/new")
            .then().statusCode(200)
            .body("vm.newCard.dueOn", equalTo("2026-10-28"))
            .body("vm.newCard.title", equalTo(""));
    }

    @Test
    void the_modal_creates_a_card_with_notes() {
        given().formParam("title", "Book flights").formParam("notes", "window seat").formParam("dueOn", "2026-10-27")
            .when().post("/cards")
            .then().statusCode(200)
            .body("vm.days[2].cards.title", contains("Book flights"))
            .body("vm.newCard", nullValue());
    }

    @Test
    void the_modal_with_a_blank_title_reopens_with_its_message_and_input() {
        given().formParam("title", "").formParam("notes", "window seat").formParam("dueOn", "2026-10-27")
            .when().post("/cards")
            .then().statusCode(400)
            .body("vm.newCard.errors.title", equalTo("Title must not be blank."))
            .body("vm.newCard.notes", equalTo("window seat"))
            .body("vm.newCard.dueOn", equalTo("2026-10-27"))
            .body("vm.days.cards.flatten()", empty());
    }

    @Test
    void cards_after_the_span_show_in_future_five_then_more() {
        for (var dueOn : new String[] {"2026-11-30", "2026-11-09", "2026-11-12", "2026-11-08", "2026-11-20", "2026-11-09"}) {
            quickAdd("due " + dueOn, dueOn).then().statusCode(200);
        }

        given().when().get("/board")
            .then().statusCode(200)
            .body("vm.future.from", equalTo("2026-11-08"))
            .body("vm.future.nearest.dateLabel", contains("8 Nov", "9 Nov", "9 Nov", "12 Nov", "20 Nov"))
            .body("vm.future.more.dateLabel", contains("30 Nov"))
            .body("vm.days.cards.flatten()", empty())
            .body("vm.days.cards.flatten().dateLabel", everyItem(nullValue()));
    }

    private static io.restassured.response.Response quickAdd(String title, String dueOn) {
        return given().formParam("title", title).formParam("dueOn", dueOn).when().post("/cards/quick-add");
    }
}
