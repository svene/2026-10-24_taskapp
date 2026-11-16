import {html} from "hono/html";
import {uiRoutes} from "./routes";
import type {HtmlResult} from "./route-types";

/**
 * Route name + view model → HTML string. Components return `HtmlResult`; `String(...)` here, at the boundary,
 * runs hono's stringify phase exactly once and yields the primitive string htmx swaps in.
 */
export function render(route: string, vm: unknown): string {
	const template = (uiRoutes as Record<string, (vm: unknown) => HtmlResult>)[route];
	return String(template ? template(vm) : html`<div>ROUTE '${route}' NOT FOUND</div>`);
}
