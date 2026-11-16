import {html} from "hono/html";
import type {BoardVM, CardVM, DayColumnVM, EditCardVM, FutureVM, MovesVM, NewCardVM} from "../html/generated/vm-types";
import type {HtmlResult} from "../html/route-types";

// Two rows of 7 day Columns, with Future as a sidebar spanning both rows (spec §3.1).
// Every request from inside the Board answers with the whole Board, morphed into #app: morph keeps focus and
// syncs input values (except the focused one: a quick-add form resets itself after a create).
export const BoardPage = (vm: BoardVM): HtmlResult => html`
	<div class="board" id="board" hx-target:inherited="#app" hx-swap:inherited="innerMorph">
		${vm.notice ? Notice(vm.notice) : ""}
		<div class="weeks">
			<div class="week">${vm.days.slice(0, 7).map(DayColumn)}</div>
			<div class="week">${vm.days.slice(7).map(DayColumn)}</div>
		</div>
		${FutureColumn(vm.future)}
		${vm.newCard ? NewCardModal(vm.newCard) : ""}
		${vm.editCard ? EditCardModal(vm.editCard) : ""}
	</div>
`;

// A rejected command (spec §3.2): a short notice above the Board, closed client-side; the next response drops it.
const Notice = (notice: string): HtmlResult => html`
	<div class="notification is-warning is-light board-notice" id="notice" role="alert">
		<button type="button" class="delete" aria-label="close" onclick="this.parentElement.remove()"></button>
		${notice}
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
		${d.overdue.length > 0 ? html`<div class="overdue-box" id="overdue" aria-label="Overdue" data-sortable data-sort="false">${d.overdue.map(Card)}</div>` : ""}
		<div class="cards" id="cards-${d.date}" data-sortable data-drop="${d.date}">${d.cards.map(Card)}</div>
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
		<div class="cards" id="cards-future" data-sortable data-drop="future" data-sort="false">
			${f.nearest.map(Card)}
			${f.more.length > 0 ? html`
				<details class="more" id="future-more">
					<summary>+${f.more.length} more</summary>
					<div class="cards" id="cards-future-more" data-sortable data-drop="future" data-sort="false">${f.more.map(Card)}</div>
				</details>` : ""}
		</div>
	</section>
`;

// Compact: the Tick checkbox, the title and markers; notes are shown in the modal. Cards in Future and in the
// Overdue box also show their date, Overdue ones with ⚠; a moved Card shows ⇢ and its original due date.
// Ticking posts to tick or untick, the card buttons (on hover or focus) to move or open the Edit modal (One-off
// Tasks only); each is answered with the Board.
const Card = (c: CardVM): HtmlResult => html`
	<div class="tcard ${c.done ? "is-done" : ""} ${c.overdue ? "is-overdue" : ""}" id="${c.id}">
		<input type="hidden" name="taskId" value="${c.taskId}">
		<input type="checkbox" class="tcard-tick" id="tick-${c.taskId}" ${c.done ? "checked" : ""}
			aria-label="${c.done ? "Untick" : "Tick off"} ${c.title}"
			hx-post="/cards/${c.taskId}/${c.done ? "untick" : "tick"}" hx-trigger="change">
		${c.dateLabel ? html`<span class="tcard-date">${c.overdue ? "⚠ " : ""}${c.dateLabel}</span>` : ""}
		<span class="tcard-title">${c.title}</span>
		${c.originalLabel ? html`<span class="tcard-original" title="Original due date ${c.originalLabel}">⇢ ${c.originalLabel}</span>` : ""}
		${c.moves || c.editable ? CardButtons(c) : ""}
	</div>
`;

// The Move buttons are the non-drag fallback (spec §3.2): absolute dates from the server, posted as the Move's
// target. A Done Card has no Move buttons, but a Done One-off Task can still be edited.
const CardButtons = (c: CardVM): HtmlResult => html`
	<span class="tcard-actions">
		${c.moves ? MoveButtons(c.taskId, c.title, c.moves) : ""}
		${c.editable ? html`<button type="button" class="tcard-move" id="edit-${c.taskId}" title="Edit ${c.title}"
			aria-label="Edit ${c.title}" hx-get="/cards/${c.taskId}/edit">✎</button>` : ""}
	</span>
`;

const MoveButtons = (taskId: number, title: string, m: MovesVM): HtmlResult => html`
	${m.toToday ? MoveButton(taskId, m.toToday, "→Today", `Move ${title} to Today`, "today") : ""}
	${MoveButton(taskId, m.plusDay, "+1d", `Move ${title} one day later`, "day")}
	${MoveButton(taskId, m.plusWeek, "+1w", `Move ${title} one week later`, "week")}
`;

const MoveButton = (taskId: number, target: string, label: string, title: string, kind: string): HtmlResult => html`
	<button type="button" class="tcard-move" id="move-${kind}-${taskId}" title="${title}" aria-label="${title}"
		hx-post="/cards/${taskId}/move" hx-vals='{"target": "${target}"}'>${label}</button>
`;

const closeModal = "this.closest('.modal').remove()";

// The New Task modal (One-off only in v1 so far).
const NewCardModal = (f: NewCardVM): HtmlResult => TaskModal({
	id: "new-card", heading: "New task", action: "/cards", submit: "Create",
	title: f.title, notes: f.notes, dueOn: f.dueOn, minDueOn: undefined, errors: f.errors,
});

// The Edit modal of a One-off Task (spec §3.2). A changed due date is a Move to the bottom of that day; the date
// picker starts at today (or the Overdue Card's own day, so it can keep it).
const EditCardModal = (f: EditCardVM): HtmlResult => TaskModal({
	id: "edit-card", heading: "Edit task", action: `/cards/${f.id}`, submit: "Save",
	title: f.title, notes: f.notes, dueOn: f.dueOn, minDueOn: f.minDueOn, errors: f.errors,
});

type TaskForm = {
	id: string; heading: string; action: string; submit: string;
	title: string; notes: string; dueOn: string; minDueOn: string | undefined; errors: Record<string, string>;
};

// One task form in a modal. It closes client-side: background, ×, Cancel or Escape.
const TaskModal = (f: TaskForm): HtmlResult => html`
	<div class="modal is-active" id="${f.id}" onkeydown="if (event.key === 'Escape') ${closeModal}">
		<div class="modal-background" onclick="${closeModal}"></div>
		<form class="modal-card" hx-post="${f.action}">
			<header class="modal-card-head">
				<p class="modal-card-title">${f.heading}</p>
				<button type="button" class="delete" aria-label="close" onclick="${closeModal}"></button>
			</header>
			<section class="modal-card-body">
				${Field(f.id, "Title", "title", f.errors["title"], html`
					<input class="input ${f.errors["title"] ? "is-danger" : ""}" id="${f.id}-title" name="title"
						value="${f.title}" autocomplete="off" autofocus>`)}
				${Field(f.id, "Notes", "notes", undefined, html`
					<textarea class="textarea" id="${f.id}-notes" name="notes" rows="3">${f.notes}</textarea>`)}
				${Field(f.id, "Due", "dueOn", f.errors["dueOn"], html`
					<input class="input ${f.errors["dueOn"] ? "is-danger" : ""}" id="${f.id}-dueOn" name="dueOn" type="date"
						value="${f.dueOn}" ${f.minDueOn ? html`min="${f.minDueOn}"` : ""} required>`)}
			</section>
			<footer class="modal-card-foot buttons">
				<button type="submit" class="button is-link">${f.submit}</button>
				<button type="button" class="button" onclick="${closeModal}">Cancel</button>
			</footer>
		</form>
	</div>
`;

const Field = (form: string, label: string, name: string, error: string | undefined, control: HtmlResult): HtmlResult => html`
	<div class="field">
		<label class="label" for="${form}-${name}">${label}</label>
		<div class="control">${control}</div>
		${error ? html`<p class="help is-danger">${error}</p>` : ""}
	</div>
`;
