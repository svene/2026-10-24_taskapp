import {render} from "./render";

declare const htmx: any;

/**
 * The `hono` htmx 4 extension: renders the hono `html` templates in the browser.
 *
 * View endpoints return a JSON envelope `{ route, vm }` (UiResponse.java) instead of an HTML fragment. This
 * hook runs the matching template on `vm` and replaces `ctx.text` with the HTML, so the normal htmx swap
 * proceeds unchanged. `htmx_after_request` is htmx 4's place to rewrite the body before the swap. Responses
 * that aren't JSON are left untouched.
 */
htmx.registerExtension("hono", {
	// htmx 4 sends `Accept: text/html`; the view endpoints only produce the JSON envelope.
	htmx_config_request: (_elt: Element, detail: any) => {
		detail.ctx.request.headers["Accept"] = "application/json, text/html;q=0.9";
	},

	htmx_after_request: (_elt: Element, detail: any) => {
		const ctx = detail.ctx;
		const contentType = ctx.response?.headers?.get?.("content-type") ?? "";
		if (!contentType.includes("application/json")) return;
		if (!ctx.text) return;
		const {route, vm} = JSON.parse(ctx.text);
		ctx.text = render(route, vm);
	},
});
