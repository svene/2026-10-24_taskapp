import {html} from "hono/html";
import type {
	BoardVM, CardVM, DayColumnVM, EditCardVM, EditOccurrenceVM, FutureVM, MovesVM, NewCardVM,
} from "../html/generated/vm-types";
import type {HtmlResult} from "../html/route-types";
import {SchedulePreview} from "../recurring/schedule";

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
		${vm.editOccurrence ? EditOccurrenceModal(vm.editOccurrence) : ""}
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
// Overdue box also show their date, Overdue ones with ⚠; an Occurrence shows ↻; a moved Card shows ⇢ and its
// original due date. Ticking posts to tick or untick, the card buttons (on hover or focus) to move or open the Edit
// modal; each is answered with the Board.
const Card = (c: CardVM): HtmlResult => html`
	<div class="tcard ${c.done ? "is-done" : ""} ${c.overdue ? "is-overdue" : ""}" id="${c.id}">
		<input type="hidden" name="taskId" value="${c.taskId}">
		<input type="checkbox" class="tcard-tick" id="tick-${c.taskId}" ${c.done ? "checked" : ""}
			aria-label="${c.done ? "Untick" : "Tick off"} ${c.title}"
			hx-post="/cards/${c.taskId}/${c.done ? "untick" : "tick"}" hx-trigger="change">
		${c.dateLabel ? html`<span class="tcard-date">${c.overdue ? "⚠ " : ""}${c.dateLabel}</span>` : ""}
		<span class="tcard-title">${c.title}</span>
		${c.recurring ? html`<span class="tcard-recurring" title="Recurring task" aria-label="Recurring task">↻</span>` : ""}
		${c.originalLabel ? html`<span class="tcard-original" title="Original due date ${c.originalLabel}">⇢ ${c.originalLabel}</span>` : ""}
		${CardButtons(c)}
	</div>
`;

// The Move buttons are the non-drag fallback (spec §3.2): absolute dates from the server, posted as the Move's
// target. A Done Card has no Move buttons, but it can still be edited.
const CardButtons = (c: CardVM): HtmlResult => html`
	<span class="tcard-actions">
		${c.moves ? MoveButtons(c.taskId, c.title, c.moves) : ""}
		<button type="button" class="tcard-move" id="edit-${c.taskId}" title="Edit ${c.title}"
			aria-label="Edit ${c.title}" hx-get="/cards/${c.taskId}/edit">✎</button>
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

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

const KINDS = [["WEEKLY", "Weekly"], ["MONTHLY_DAY", "Monthly on day"], ["MONTHLY_LAST", "Monthly on the last day"]];

// The New Task modal (spec §3.2): One-off (a due date) or Repeats on a calendar (Weekly: weekdays and every N weeks;
// Monthly on day N; Monthly on the last day; each from a Start date). Only the chosen kind's fields are shown (CSS
// :has); the server validates only those. Any change in the form refreshes the preview of the next dates, swapped
// into its own box rather than morphing the Board.
const NewCardModal = (f: NewCardVM): HtmlResult => Modal("new-card", "New task", "/cards", "Create", html`
	${TitleNotes("new-card", f.title, f.notes, f.errors)}
	<div class="field">
		<div class="control repeats">
			<label class="radio"><input type="radio" name="repeats" value="once" id="new-card-once"
				${f.repeats === "once" ? "checked" : ""}> One-off</label>
			<label class="radio"><input type="radio" name="repeats" value="calendar" id="new-card-calendar"
				${f.repeats === "calendar" ? "checked" : ""}> Repeats on a calendar</label>
		</div>
	</div>
	<div class="once-only">
		${DueField("new-card", f.dueOn, undefined, f.errors["dueOn"])}
	</div>
	<div class="calendar-only">
		${Field("new-card", "Repeats", "kind", f.errors["kind"], html`
			<div class="select"><select id="new-card-kind" name="kind">
				${KINDS.map(([value, label]) => html`
					<option value="${value}" ${f.kind === value ? "selected" : ""}>${label}</option>`)}
			</select></div>`)}
		<div class="weekly-only">
			<div class="field">
				<span class="label">On</span>
				<div class="control weekdays" id="new-card-weekdays">
					${WEEKDAYS.map((day, i) => html`
						<label class="checkbox"><input type="checkbox" name="weekdays" value="${i + 1}" id="new-card-weekday-${i + 1}"
							${f.weekdays.includes(i + 1) ? "checked" : ""}> ${day}</label>`)}
				</div>
				${f.errors["weekdays"] ? html`<p class="help is-danger">${f.errors["weekdays"]}</p>` : ""}
			</div>
			${Field("new-card", "Every N weeks", "everyWeeks", f.errors["everyWeeks"], html`
				<input class="input every-weeks ${f.errors["everyWeeks"] ? "is-danger" : ""}" id="new-card-everyWeeks"
					name="everyWeeks" type="number" min="1" value="${f.everyWeeks}">`)}
		</div>
		<div class="monthly-day-only">
			${Field("new-card", "Day of the month", "monthDay", f.errors["monthDay"], html`
				<input class="input month-day ${f.errors["monthDay"] ? "is-danger" : ""}" id="new-card-monthDay"
					name="monthDay" type="number" min="1" max="28" value="${f.monthDay}">`)}
		</div>
		${Field("new-card", "Starts", "startsOn", f.errors["startsOn"], html`
			<input class="input ${f.errors["startsOn"] ? "is-danger" : ""}" id="new-card-startsOn" name="startsOn"
				type="date" value="${f.startsOn}">`)}
		<div class="field">
			<span class="label">Next dates</span>
			<div id="new-card-preview" hx-get="/schedules/preview" hx-include="closest form"
				hx-trigger="change from:closest form" hx-target="this" hx-swap="innerHTML">${SchedulePreview(f.preview)}</div>
		</div>
	</div>
`);

// The Edit modal of a One-off Task (spec §3.2). A changed due date is a Move to the bottom of that day; the date
// picker starts at today (or the Overdue Card's own day, so it can keep it).
const EditCardModal = (f: EditCardVM): HtmlResult => Modal("edit-card", "Edit task", `/cards/${f.id}`, "Save", html`
	${TitleNotes("edit-card", f.title, f.notes, f.errors)}
	${DueField("edit-card", f.dueOn, f.minDueOn, f.errors["dueOn"])}
`);

// The Edit modal of an Occurrence (spec §3.2): its Recurring Task's title, notes and Schedule read-only; the due
// date (a Move, as above) and Done editable.
const EditOccurrenceModal = (f: EditOccurrenceVM): HtmlResult =>
	Modal("edit-occurrence", "Edit occurrence", `/cards/${f.id}/occurrence`, "Save", html`
		<p class="title is-5" id="edit-occurrence-title">${f.title} <span title="Recurring task">↻</span></p>
		${f.notes ? html`<p class="occurrence-notes" id="edit-occurrence-notes">${f.notes}</p>` : ""}
		<p class="help" id="edit-occurrence-schedule">${f.schedule}</p>
		${DueField("edit-occurrence", f.dueOn, f.minDueOn, f.errors["dueOn"])}
		<div class="field">
			<label class="checkbox"><input type="checkbox" name="done" id="edit-occurrence-done" ${f.done ? "checked" : ""}>
				Done</label>
		</div>
	`);

const TitleNotes = (form: string, title: string, notes: string, errors: Record<string, string>): HtmlResult => html`
	${Field(form, "Title", "title", errors["title"], html`
		<input class="input ${errors["title"] ? "is-danger" : ""}" id="${form}-title" name="title"
			value="${title}" autocomplete="off" autofocus>`)}
	${Field(form, "Notes", "notes", undefined, html`
		<textarea class="textarea" id="${form}-notes" name="notes" rows="3">${notes}</textarea>`)}
`;

const DueField = (form: string, dueOn: string, minDueOn: string | undefined, error: string | undefined): HtmlResult =>
	Field(form, "Due", "dueOn", error, html`
		<input class="input ${error ? "is-danger" : ""}" id="${form}-dueOn" name="dueOn" type="date"
			value="${dueOn}" ${minDueOn ? html`min="${minDueOn}"` : ""}>`);

// A form in a modal. It closes client-side: background, ×, Cancel or Escape.
const Modal = (id: string, heading: string, action: string, submit: string, body: HtmlResult): HtmlResult => html`
	<div class="modal is-active" id="${id}" onkeydown="if (event.key === 'Escape') ${closeModal}">
		<div class="modal-background" onclick="${closeModal}"></div>
		<form class="modal-card" hx-post="${action}">
			<header class="modal-card-head">
				<p class="modal-card-title">${heading}</p>
				<button type="button" class="delete" aria-label="close" onclick="${closeModal}"></button>
			</header>
			<section class="modal-card-body">${body}</section>
			<footer class="modal-card-foot buttons">
				<button type="submit" class="button is-link">${submit}</button>
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
