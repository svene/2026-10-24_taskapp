import {html} from "hono/html";
import type {BoardVM, DayColumnVM, FutureVM} from "../html/generated/vm-types";
import type {HtmlResult} from "../html/route-types";

// Two rows of 7 day Columns, with Future as a sidebar spanning both rows (spec §3.1).
export const BoardPage = (vm: BoardVM): HtmlResult => html`
	<div class="board" id="board">
		<div class="weeks">
			<div class="week">${vm.days.slice(0, 7).map(DayColumn)}</div>
			<div class="week">${vm.days.slice(7).map(DayColumn)}</div>
		</div>
		${FutureColumn(vm.future)}
	</div>
`;

const DayColumn = (d: DayColumnVM): HtmlResult => html`
	<section class="board-column ${d.today ? "is-today" : ""} ${d.weekend ? "is-weekend" : ""}" id="${d.id}" data-date="${d.date}">
		<header class="column-head">
			<span class="weekday">${d.weekday}</span>
			<span class="day-month">${d.dayMonth}</span>
		</header>
		<div class="cards"></div>
	</section>
`;

const FutureColumn = (f: FutureVM): HtmlResult => html`
	<section class="board-column is-future" id="col-future">
		<header class="column-head">
			<span class="weekday">Future</span>
			<span class="day-month">${f.fromLabel}</span>
		</header>
		<div class="cards"></div>
	</section>
`;
