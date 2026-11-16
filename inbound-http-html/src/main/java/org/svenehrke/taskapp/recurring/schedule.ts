import {html} from "hono/html";
import type {SchedulePreviewVM} from "../html/generated/vm-types";
import type {HtmlResult} from "../html/route-types";

// The New Task modal's live preview of a calendar Schedule (spec §3.2): its first dates, or why there are none yet.
// Rendered inside #new-card-preview, both with the modal and on its own by /schedules/preview.
export const SchedulePreview = (vm: SchedulePreviewVM): HtmlResult => vm.message
	? html`<p class="help" id="new-card-preview-message">${vm.message}</p>`
	: html`<ol class="schedule-preview" id="new-card-preview-dates">${vm.dates.map(d => html`<li>${d}</li>`)}</ol>`;
