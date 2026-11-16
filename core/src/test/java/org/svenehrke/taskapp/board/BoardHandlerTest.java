package org.svenehrke.taskapp.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class BoardHandlerTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    @Test
    void board_spans_fourteen_days_from_today_then_future() {
        var sunday = LocalDate.of(2026, 10, 25);
        var handler = new BoardHandler(Clock.fixed(sunday.atTime(23, 30).atZone(ZONE).toInstant(), ZONE));

        var board = handler.board();

        assertThat(board.days()).hasSize(Board.SPAN_DAYS);
        assertThat(board.days().getFirst().date()).isEqualTo(sunday);
        assertThat(board.days().getLast().date()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(board.future().from()).isEqualTo(LocalDate.of(2026, 11, 8));
    }
}
