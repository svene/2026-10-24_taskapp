import type {UiRoute} from "./generated/vm-types";
import type {HtmlResult} from "./route-types";
import {BoardPage} from "../board/board";
import {SchedulePreview} from "../recurring/schedule";

/**
 * One template per UiRoute. The route names come from the Java UiRoute enum (via typescript-generator), and
 * `satisfies` makes a missing or misspelled entry a TypeScript error.
 */
export const uiRoutes = {
	BoardPage: (vm: any) => BoardPage(vm),
	SchedulePreview: (vm: any) => SchedulePreview(vm),
} satisfies Record<UiRoute, (vm: any) => HtmlResult>;
