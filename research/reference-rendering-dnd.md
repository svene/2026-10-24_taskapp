# How the reference renders HTML in the browser, and how drag-and-drop fits

Research for [#3](https://github.com/svene/2026-10-24_taskapp/issues/3). Feeds the Board prototype ticket [#5](https://github.com/svene/2026-10-24_taskapp/issues/5).

Sources:
- **Reference:** [svene/2026-07-26_quarkus-hexagon-demo-mm-per-tech](https://github.com/svene/2026-07-26_quarkus-hexagon-demo-mm-per-tech) at commit `b2f8493`, module `inbound-http-html`. Paths below are relative to `inbound-http-html/src/main/` and abbreviate `java/org/svenehrke/triptychdemo/cross/` as `cross/`.
- **htmx 4:** the vendored `resources/META-INF/resources/js/htmx.org/4.0.0/htmx.js`, plus [four.htmx.org](https://four.htmx.org/).
- **SortableJS:** the [GitHub README](https://github.com/SortableJS/Sortable), the `Sortable.js` 1.15.7 source from jsDelivr, and npm metadata.
- **Native DnD:** [MDN, HTML Drag and Drop API](https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API).

## TL;DR

- **Rendering pipeline.** The server never renders HTML fragments. A static shell page loads htmx 4, Bulma and one esbuild bundle (`hx-hono.js`). It has one element, `#app`, that runs `hx-get` on `load`. Every view endpoint returns the JSON `{route, vm}`. A 27-line htmx 4 extension (`hono`) rewrites `ctx.text` in the `htmx_after_request` hook to the result of the hono `html` template that `route` names. After that, htmx swaps it like an ordinary HTML response. The VM records and the `UiRoute` enum are Java. typescript-generator turns them into `vm-types.d.ts`, and `tsc` fails the build if a route has no template.
- **Drag-and-drop.** Use SortableJS (MIT, no dependencies, 1.15.7). It fits the pipeline without changing it:
  - Each Column is a `<form>` holding its cards. Each card carries a hidden `<input name="taskId">`.
  - Each form has `hx-post="/board/columns/{COLUMN}" hx-trigger="add, update"` (not the classic example's `end`; see section 4).
  - The server applies the move and returns the `Board` envelope. htmx swaps it into `#board` with `innerMorph`, so the server's state always wins.
  - A keyboard/button fallback that posts to the same endpoints is cheap.

## 1. The rendering pipeline in the reference

### 1.1 Pieces and responsibilities

| Piece | Where | Role |
|---|---|---|
| Static shell | `resources/shells/{shop,admin,locations,audit-log}.html` | Full HTML page: Bulma CSS, `theme.js`, a `<meta name="htmx-config">`, then the scripts in a fixed order (`htmx.js`, `hx-hono.js`, `hx-sse.js`, `hx-live.js`). Body holds `<div id="app" hx-get="/shop/page" hx-trigger="load" hx-swap="innerHTML">`. |
| Shell endpoint | e.g. `ShopReceiver.shell()`: `GET /shop`, `text/html` | `PageShell.render("/shells/shop.html", Map.of())` reads the classpath file and replaces `{{key}}` placeholders with server-only values. Shells live under `shells/`, not `META-INF/resources/`, so they are only reachable through the receiver. |
| View endpoints | `GET /shop/page`, `GET /shop/inventory-fragment`, ... (`application/json`) | Return `UiResponse.of(UiRoute.X, new XVM(...))`. |
| `UiResponse` | `cross/UiResponse.java` | `record UiResponse(String route, Object vm)`, the JSON envelope `{"route":"ShopPage","vm":{...}}`. `UiResponse.response(status, route, vm)` returns the same envelope with a non-200 status (400/409 validation re-renders). |
| `UiRoute` | `cross/UiRoute.java` | Enum of every renderable view. |
| `*VM` records | `cross/*VM.java` | View models owned by the adapter, not core types. Receivers map core results to them (e.g. `ProductRowVM::of`). |
| typescript-generator | `pom.xml`, `cz.habarta.typescript-generator-maven-plugin` 4.1.1, phase `process-classes` | Class pattern `org.svenehrke.triptychdemo.cross.*VM` plus class `UiRoute`, written to `cross/generated/vm-types.d.ts` (git-ignored). Settings: `mapClasses=asInterfaces`, `mapEnum=asUnion`, `jsonLibrary=jackson2`, `outputKind=module`. |
| `routes.ts` | `cross/routes.ts` | `uiRoutes = { ShopPage: vm => ShopPage(vm), ... } satisfies Record<UiRoute, (vm:any) => HtmlResult>`. A missing or misspelled route is a TypeScript error. |
| Templates | `cross/{shop,admin,location,audit-log}.ts` | Components written with hono's `html` tagged template, typed with the generated VM interfaces, e.g. `ShopPage = (vm: ShopPageVM): HtmlResult => html\`...\``. They nest (`${ShopProducts({...})}`) and carry the htmx attributes for the next interaction. hono `html` escapes interpolated values. |
| `render.ts` | `cross/render.ts` | `render(route, vm)` looks up the template and returns `String(template(vm))` (hono's stringify runs once, at the boundary). An unknown route gives `<div>ROUTE 'x' NOT FOUND</div>`. |
| `hx-hono.ts` | `cross/hx-hono.ts` | `htmx.registerExtension("hono", {...})` with two hooks. `htmx_config_request` sets `Accept: application/json, text/html;q=0.9`. `htmx_after_request` parses `ctx.text` as `{route, vm}` and sets `ctx.text = render(route, vm)`, but only if the response content-type is `application/json` and the body is not empty. |
| Bundle | `package.json`: `esbuild cross/hx-hono.ts --bundle --platform=browser --format=iife`, output `resources/META-INF/resources/js/hono/hx-hono.js` (git-ignored) | hono 4.13.12, esbuild 0.28.2, TypeScript 7.0.2. Ships inside the module jar and is served by Quarkus as a static resource. |
| Build wiring | `pom.xml`, `frontend-maven-plugin` 2.0.2, Node v24.21.0 | `initialize`: install Node and run `npm ci`. `generate-resources`: `npm run build` (esbuild only strips types, so it doesn't need the generated types). `process-classes`: typescript-generator, then `npm run typecheck` (`tsc --noEmit`). typescript-generator must be declared before frontend-maven-plugin because both run in the same phase. |
| Vendored JS | `resources/META-INF/resources/js/htmx.org/4.0.0/{htmx.js, ext/hx-sse.js, ext/hx-live.js}`, `css/bulma.min.css` | Static files checked in, not from npm. |

### 1.2 Request flow (one interaction)

```
browser                              Quarkus (inbound-http-html)                   core
GET /shop  ───────────────────────▶  ShopReceiver.shell() → shells/shop.html
#app hx-trigger=load
  GET /shop/page  (Accept: application/json…)  ─▶  ShopReceiver.page() ─▶ ProductsHandler.listAll()
  ◀── 200 {"route":"ShopPage","vm":{products:[…],errors:[]}}
hono ext (htmx_after_request): ctx.text = render("ShopPage", vm)
htmx swaps HTML into #app, processes hx-* attributes in it
form hx-post="/shop/checkout" hx-target="#app"
  POST (form-urlencoded) ─────────────────────▶  ShopReceiver.checkout() ─▶ PurchaseHandler.checkout()
  ◀── 200/400/409 {"route":"ShopPage","vm":{…, errors:[…]}}   → rendered, swapped into #app
```

Verified details:

- **Hook placement.** In `htmx.js` 4.0.0, the order is: fetch, `htmx:before:response`, `ctx.text = await response.text()`, `htmx:after:request`, status handling, `swap(ctx)` (around lines 586–615). Rewriting `ctx.text` in `htmx_after_request` therefore changes what gets swapped. The [extension authoring guide](https://four.htmx.org/docs/extension-authoring-guide) lists `htmx_config_request`, `htmx_after_request`, `htmx_before_swap` and others as hooks, but it does not say a hook may replace `ctx.text`. That behaviour is visible only in the source.
- **Extensions are global.** `registerExtension()` adds the hooks to a global table (around `htmx.js` line 227). There is no `hx-ext` on elements. `htmx.config.extensions`, if set, acts as a whitelist. So every htmx request is rendered, including requests made with `htmx.ajax()` (which goes through the same `#handleTriggerEvent` path).
- **Non-JSON responses pass through.** Mutation endpoints that only need to clear an error return `200 ""` as `text/html` (`AdminReceiver.orderAccepted()`). The comment there notes that htmx never swaps a 204. The shells set `noSwap: [204, 304, "5xx"]`.
- **Status handling.** 400 and 409 are swapped normally (checkout re-renders the page with errors). Only 204, 304 and 5xx are not swapped.
- **Partial refresh.** Sub-regions have their own GET endpoint and template (e.g. `#shop-products` with `hx-get="/shop/inventory-fragment" hx-trigger="inventoryChanged from:body" hx-sync="this:replace" hx-swap="innerMorph"`). In the reference, the trigger comes from an SSE stream (`hx-sse:connect="/inventory/events"` in the shell). SSE is out of scope for taskapp (#1).
- **Morphing.** `innerMorph` matches elements by `id`, so stable `id`s on rows keep typed input values and focus ([morphing guide](https://four.htmx.org/docs/morphing-swaps-guide): "An element that has the same `id` will remain in the new DOM").
- **Client-side UI state** uses htmx 4's `hx-live` extension (`hx-live="this.disabled = …"`, `hx-on:click="take('.is-active')"`), not Alpine.
- **No inherited attributes.** htmx 4 has `implicitInheritance: false` by default. `hx-target` and `hx-swap` must sit on the element that issues the request, or be written with `:inherited`.
- **Explicit endpoints.** The reference uses one endpoint per view instead of a generic `/uiroute/{name}` dispatcher (`docs/architecture/browser-templating_wip.md`, progress log 2026-09-30).
- **Known limitation.** `hx-multipart` (and anything else that calls `htmx.swap()` directly) bypasses `htmx_after_request`, so the `hono` extension would not render those responses (`browser-templating_wip.md`, open questions). This doesn't matter for taskapp unless such a feature is added.

### 1.3 What taskapp copies, minimally

- One shell (`shells/board.html`) and `GET /` (or `/board`) to serve it, with `#app` loading `GET /board/page`. Drop `hx-sse.js`; keep `hx-hono.js` straight after `htmx.js`.
- Copy `UiRoute`, `UiResponse`, `PageShell`, `render.ts`, `routes.ts`, `route-types.ts`, `hx-hono.ts`, `package.json`, `tsconfig.json` and the two pom plugins. Adjust the package in the typescript-generator `classPatterns`. `cross/generated/` and `js/hono/` stay git-ignored.

## 2. Options for drag-and-drop

### 2.1 SortableJS (recommended)

- **Library facts.** MIT licence, "No jQuery or framework required", supports touch devices ([README](https://github.com/SortableJS/Sortable)). Latest version 1.15.7, published 2026-02-11 (`npm view sortablejs`).
- **Moving between lists.** Lists with the same `group` value can exchange items. `handle`, `filter` and `delay`/`delayOnTouchOnly` are available. `Sortable.get(el)` returns an element's instance; `destroy()` removes it.
- **Events are bubbling DOM `CustomEvent`s**, not just callbacks. In `Sortable.js` 1.15.7, `dispatchEvent()` creates `new CustomEvent(name, {bubbles: true, cancelable: true})` and sets `evt.to`, `evt.from`, `evt.item`, `evt.oldIndex` and `evt.newIndex` on it (source lines 769–814). Which element receives each event (lines 1965–2031):
  - Cross-list drop: **`add` on the destination list**, `remove` on the source list, `sort` on both.
  - Same-list reorder: **`update` on that list**, then `sort`.
  - Always: **`end` on the source list (`rootEl`)**, even when nothing moved.
- **Consequence.** htmx can listen to these events directly with `hx-trigger`, which accepts custom events ("Custom events work too"; comma-separated lists allowed, [hx-trigger reference](https://four.htmx.org/reference/attributes/hx-trigger)).
- **The canonical htmx 4 pattern** ([four.htmx.org/examples/sortable](https://four.htmx.org/examples/sortable/)):
  - `<form class="sortable" hx-post="/items" hx-trigger="end">` with one hidden `<input name="item">` per row.
  - `hx-on:load="new Sortable(this, {..., onEnd() { this.option('disabled', true) }})"` and `hx-on::after:swap="Sortable.get(this).option('disabled', false)"`.
  - Dragging reorders the DOM, so the form posts the inputs in their new order.

### 2.2 Native HTML5 drag-and-drop

- Each Column cancels `dragover` with `preventDefault()` and handles `drop`. Data travels as strings in `dataTransfer`, which can only be written in `dragstart` and read in `drop` ([MDN](https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API)).
- No library is needed, but the app would write by hand everything Sortable provides:
  - placeholder and animation
  - computing the drop index for ordering within a Column
  - visual feedback
  - `draggable` elements lose normal mouse text selection (MDN)
- MDN says nothing about touch or keyboard support.
- It is still a good fit if the Board only moves cards between Columns and never orders them: a `drop` handler can call `htmx.ajax('POST', '/board/tasks/{id}/move', {values: {column}, target: '#board', swap: 'innerMorph'})`, and that response goes through the `hono` extension too.

### 2.3 Move buttons, no DnD

- `<button hx-post="/board/tasks/{id}/move" hx-vals='{"column":"DOING"}' hx-target="#board" hx-swap="innerMorph">→</button>`. This is pure reference style with zero extra JS, and it works from the keyboard.
- It is worth having alongside DnD as the accessible path. It costs only the template; the endpoint can be shared.

## 3. How SortableJS fits the pipeline (sketch for #5)

```html
<!-- shell, after htmx.js and hx-hono.js -->
<script src="/js/sortablejs/1.15.7/Sortable.min.js"></script>
```

```ts
// board.ts (hono template); BoardVM / ColumnVM / CardVM from vm-types.d.ts
const Column = (c: ColumnVM): HtmlResult => html`
  <form id="col-${c.status}" class="board-column"
        hx-post="/board/columns/${c.status}" hx-trigger="add, update"
        hx-target="#board" hx-swap="innerMorph"
        hx-on:load="new Sortable(this, {group: 'board', animation: 150,
                      onEnd() { this.option('disabled', true) }})"
        hx-on::after:swap="Sortable.get(this)?.option('disabled', false)">
    ${c.cards.map(Card)}
  </form>`;
const Card = (t: CardVM): HtmlResult => html`
  <div class="box" id="task-${t.id}">
    <input type="hidden" name="taskId" value="${t.id}">${t.title}
  </div>`;
```

- **Server side.** `POST /board/columns/{column}` receives `taskId=…&taskId=…` in the new order of that Column. It works out what moved (a task now listed here whose status was different, or a changed order) and calls a core Handler. It responds with `UiResponse.of(UiRoute.Board, boardVM)`. A rejected move returns 409 with the same envelope plus an error, as `ShopReceiver.checkout` does. In both cases the morph puts the card back where the server says it is.
- **Why `add, update` and not `end`.** For a cross-Column drop, `end` fires on the *source* list ([§2.1](#21-sortablejs-recommended)). With the classic example's `hx-trigger="end"`, the source form would post a list that no longer contains the moved card, and the destination would never post. `add` fires on the destination; `update` covers reordering within one Column. (A single board-wide form with `end` would also work, but it would have to encode which Column each input belongs to.)
- **Disabling during the request.** `onEnd` runs on the source Sortable only, so after a cross-Column drop the destination stays enabled while the request is in flight. If that matters, set `disabled` on every Column or use `hx-sync`. This is a detail for the prototype.
- **Morph versus Sortable.** The Sortable instance is stored on the Column element (an expando). With stable `id`s (`col-{status}`, `task-{id}`), `innerMorph` keeps the same Column elements, so the instance survives; `Sortable.get(this)` re-enables it. If `#board` were swapped with `innerHTML`, the Columns would be new elements and `hx-on:load` would create fresh instances. Either way works.
- **Reinitialising after swaps.** Alternatives to `hx-on:load` are `htmx.onLoad(cb)` (in 4.0.0 it listens to `htmx:after:process`, `htmx.js` around line 1529) or an init module in the esbuild bundle.

## 4. Constraints #5 must respect

1. **The server is the source of truth for a card's Column and position.** Every DnD drop is a normal htmx request whose JSON `Board` envelope is rendered by the `hono` extension and morphed in. There is no client-side board state and no optimistic-only moves.
2. **Use `hx-trigger="add, update"` on each Column**, not the classic `end` (section 3). Alternatively, call `htmx.ajax` from a Sortable callback; it still goes through the `hono` extension.
3. **Use stable `id`s** on the board, the Columns and the cards, and swap with `innerMorph`, as the reference does for its refreshed regions.
4. **Add a `UiRoute` for every new view** (e.g. `Board`, and possibly `CardEditor`), with a template in `routes.ts`; `tsc` enforces this. Name VM records `*VM` so typescript-generator picks them up.
5. **Responses.** Mutation endpoints return the envelope (200, or 400/409 with errors). Use `200 ""` (`text/html`) only to clear a target. Do not use 204 for anything that must swap.
6. **Ship the library as a vendored, version-pinned file** under `META-INF/resources/js/` like htmx (or as an npm dependency imported into the esbuild bundle). Do not use a CDN; the app runs locally. Load it after `htmx.js` and `hx-hono.js`.
7. **No SSE.** A move refreshes only through its own response. Anything else that has to refresh after a change (e.g. a Recurring Task's next occurrence appearing when it is moved to Done) must come back in that same `Board` response, or through an `HX-Trigger` response header and a `from:body` listener.
8. **Accessibility and fallback.** Pointer DnD has no keyboard path. Move buttons posting to the same endpoints are cheap (section 2.3).

## 5. Fog and new questions this surfaced

- **Card order within a Column.**
  - Sortable makes reordering within a Column natural.
  - But the Task fields v1 in #1 (title, notes, due date, recurrence) have no position/rank, so this needs a decision:
    - Is order user-defined (a persisted rank) or derived (e.g. by due date)?
  - If derived, disable reordering within a Column and listen only to `add`; `sort: false` on Sortable allows dropping into a list but not reordering it.
- **What a drop into Done means**:
  - The Done column shows only recent completions in server-defined order, so the drop position there is meaningless.
  - Moving a Recurring Task occurrence to Done also affects the next occurrence.
  - The Board response has to reflect both. This ties into the Recurring Task lifecycle ticket.
- **Allowed transitions.** Are all moves allowed (Done back to To Do? Doing back to To Do?)? Sortable's `group.put`/`pull` can restrict them on the client, but the server must enforce them anyway.
- **Move endpoint shape.** Either per-Column order lists (`POST /board/columns/{column}` with a `taskId` list) or a single move command (`POST /board/tasks/{id}/move` with column and index). Which one depends on the ordering decision. A single move command maps more directly onto a core Handler.
- **Packaging Sortable.** A vendored file or an npm dependency bundled by esbuild. Minor; the prototype can settle it.
