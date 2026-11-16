import {html} from "hono/html";
import type {BoardVM, CardVM, DayColumnVM, FutureVM, NewCardVM} from "../html/generated/vm-types";
import type {HtmlResult} from "../html/route-types";

// Two rows of 7 day Columns, with Future as a sidebar spanning both rows (spec §3.1).
// Every request from inside the Board answers with the whole Board, morphed into #app: morph keeps focus and
// syncs input values (except the focused one: a quick-add form resets itself after a create).
export const BoardPage = (vm: BoardVM): HtmlResult => html`
	<div class="board" id="board" hx-target:inherited="#app" hx-swap:inherited="innerMorph">
		<div class="weeks">
			<div class="week">${vm.days.slice(0, 7).map(DayColumn)}</div>
			<div class="week">${vm.days.slice(7).map(DayColumn)}</div>
		</div>
		${FutureColumn(vm.future)}
		${vm.newCard ? NewCardModal(vm.newCard) : ""}
	</div>
`;

const AddButton = (dueOn: string): HtmlResult => html`
	<button type="button" class="button is-small is-ghost add-card" title="New task" aria-label="New task"
		hx-get="/cards/new?dueOn=${dueOn}">+</button>
`;

const DayColumn = (d: DayColumnVM): HtmlResult => html`
	<section class="board-column ${d.today ? "is-today" : ""} ${d.weekend ? "is-weekend" : ""}" id="${d.id}" data-date="${d.date}">
		<header class="column-head">
			<span class="weekday">${d.weekday}</span>
			<span class="day-month">${d.dayMonth}</span>
			${AddButton(d.date)}
		</header>
		<div class="cards">${d.cards.map(Card)}</div>
		<form class="quick" id="quick-${d.date}" hx-post="/cards/quick-add"
			hx-on::after:swap="if (ctx.response?.status === 200) this.reset()">
			<input type="hidden" name="dueOn" value="${d.date}">
			<input class="input is-small ${d.quickAddError ? "is-danger" : ""}" id="quick-title-${d.date}" name="title"
				placeholder="+ add" autocomplete="off" value="">
			${d.quickAddError ? html`<p class="help is-danger">${d.quickAddError}</p>` : ""}
		</form>
	</section>
`;

const FutureColumn = (f: FutureVM): HtmlResult => html`
	<section class="board-column is-future" id="col-future">
		<header class="column-head">
			<span class="weekday">Future</span>
			<span class="day-month">${f.fromLabel}</span>
			${AddButton(f.from)}
		</header>
		<div class="cards">
			${f.nearest.map(Card)}
			${f.more.length > 0 ? html`
				<details class="more" id="future-more">
					<summary>+${f.more.length} more</summary>
					<div class="cards">${f.more.map(Card)}</div>
				</details>` : ""}
		</div>
	</section>
`;

// Compact: the title only; notes are shown in the modal. Future Cards also show their date.
const Card = (c: CardVM): HtmlResult => html`
	<div class="tcard" id="${c.id}">
		<input type="hidden" name="taskId" value="${c.taskId}">
		${c.dateLabel ? html`<span class="tcard-date">${c.dateLabel}</span>` : ""}
		<span class="tcard-title">${c.title}</span>
	</div>
`;

const closeModal = "this.closest('.modal').remove()";

// The New Task modal (One-off only in v1 so far). It closes client-side: background, ×, Cancel or Escape.
const NewCardModal = (f: NewCardVM): HtmlResult => html`
	<div class="modal is-active" id="new-card" onkeydown="if (event.key === 'Escape') ${closeModal}">
		<div class="modal-background" onclick="${closeModal}"></div>
		<form class="modal-card" hx-post="/cards">
			<header class="modal-card-head">
				<p class="modal-card-title">New task</p>
				<button type="button" class="delete" aria-label="close" onclick="${closeModal}"></button>
			</header>
			<section class="modal-card-body">
				${Field("Title", "title", f.errors["title"], html`
					<input class="input ${f.errors["title"] ? "is-danger" : ""}" id="new-card-title" name="title"
						value="${f.title}" autocomplete="off" autofocus>`)}
				${Field("Notes", "notes", undefined, html`
					<textarea class="textarea" id="new-card-notes" name="notes" rows="3">${f.notes}</textarea>`)}
				${Field("Due", "dueOn", f.errors["dueOn"], html`
					<input class="input ${f.errors["dueOn"] ? "is-danger" : ""}" id="new-card-dueOn" name="dueOn" type="date"
						value="${f.dueOn}" required>`)}
			</section>
			<footer class="modal-card-foot buttons">
				<button type="submit" class="button is-link">Create</button>
				<button type="button" class="button" onclick="${closeModal}">Cancel</button>
			</footer>
		</form>
	</div>
`;

const Field = (label: string, name: string, error: string | undefined, control: HtmlResult): HtmlResult => html`
	<div class="field">
		<label class="label" for="new-card-${name}">${label}</label>
		<div class="control">${control}</div>
		${error ? html`<p class="help is-danger">${error}</p>` : ""}
	</div>
`;
