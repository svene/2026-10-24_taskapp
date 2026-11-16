import Sortable from "sortablejs";

declare const htmx: any;

/**
 * Drag-and-drop on the Board (spec §3.2, §4.4). Every card list marked `data-sortable` gets one SortableJS instance,
 * all in one group. A drop becomes one Move, posted like the card buttons do:
 * `POST /cards/{id}/move` with `target` (the list's `data-drop`: a date or `future`) and `before` (the Card now
 * below it, if any). The answer is the Board, morphed into #app; a rejected drop (409) re-renders it as it was.
 *
 * - The Overdue box (no `data-drop`) accepts no drops and cannot be reordered; Cards can be dragged out of it.
 * - Future cannot be reordered, and a drop on it goes to the bottom of the day after the span, so it sends no `before`.
 * - Done Cards don't drag, but open Cards can be dropped above or below them.
 */
const initSortables = (): void => {
	document.querySelectorAll<HTMLElement>("[data-sortable]").forEach(list => {
		if (Sortable.get(list)) return; // morph keeps the element and its instance; only new lists need one
		Sortable.create(list, {
			group: {name: "board", put: list.dataset.drop !== undefined},
			sort: list.dataset.sort !== "false",
			draggable: ".tcard",
			filter: ".is-done",
			preventOnFilter: false, // keeps the Done Card's checkbox clickable
			// on add / update, never end: exactly one of them fires per drop, on the list it landed in
			onAdd: move,
			onUpdate: move,
			animation: 120,
			ghostClass: "tcard-ghost",
			emptyInsertThreshold: 24,
			forceFallback: true, // HTML5 drag-and-drop can't be driven by Chrome automation
			fallbackTolerance: 4, // a click on the checkbox or a card button is not a drag
		});
	});
};

const move = (evt: Sortable.SortableEvent): void => {
	const list = evt.to;
	const target = list.dataset.drop;
	if (target === undefined) return;
	const values: Record<string, string> = {target};
	const before = target === "future" ? null : nextCard(evt.item);
	if (before) values.before = taskId(before);
	// the list inherits hx-target #app and hx-swap innerMorph from #board, like every other request on the Board
	htmx.ajax("POST", `/cards/${taskId(evt.item)}/move`, {source: list, values});
};

const nextCard = (card: HTMLElement): HTMLElement | null => {
	let next = card.nextElementSibling;
	while (next && !next.matches(".tcard")) next = next.nextElementSibling;
	return next as HTMLElement | null;
};

const taskId = (card: HTMLElement): string =>
	card.querySelector<HTMLInputElement>("input[name=taskId]")!.value;

document.addEventListener("htmx:after:swap", initSortables);
