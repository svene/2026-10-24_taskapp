# Architectural concepts

How Taskapp is built, and why. What the app does is in [`docs/spec.md`](docs/spec.md), its vocabulary in
[`GLOSSARY.md`](GLOSSARY.md). The architecture follows the reference
[svene/2026-07-26_quarkus-hexagon-demo-mm-per-tech](https://github.com/svene/2026-07-26_quarkus-hexagon-demo-mm-per-tech);
where Taskapp differs, [`docs/architecture`](docs/architecture/README.md) says so.

## Hexagonal, one Maven module per adapter technology

| Module | Role |
|---|---|
| `core` | Domain + use cases, and every port. No JDBC, no HTTP. |
| `inbound-http-html` | Receivers: browser requests → Handlers; answers with a JSON `{route, vm}` envelope. |
| `outbound-sqlite` | Services: implement the SPIs with plain SQL over JDBC against one SQLite file; Flyway migrations. |
| `app-server` | The only deployable: assembles the others, produces the `Clock`, holds `application.properties`. |

The module graph keeps the layering honest at compile time: `core` depends on no adapter, and no adapter depends
on another. What it can't see, ArchUnit checks (`app-server`, `ArchitectureTest`).

## Naming (Triptych)

- **`*Handler`** (core): an inbound port. Its public methods are the use cases; there is no `*API` interface.
  Handlers never call Handlers; logic they share sits in a plain bean such as `OccurrenceProducer`.
- **`*SPI`** (core): an outbound port, a public interface.
- **`*Receiver`** (inbound adapter): calls Handlers only, never an SPI.
- **`*Service`** (outbound adapter): implements an SPI.

## Packages

Base package `org.svenehrke.taskapp`, flat: `card`, `recurring`, `board`. Every module uses the same package names,
so a feature's Handler, Receiver and Service sit in the same package in their three modules. Allowed dependencies:
`board` → `card`, `recurring`; `card` → `recurring`. `recurring` never depends on `card`.

Adapter-only plumbing gets its own package next to them: `html` (the rendering envelope, `UiRoute`, page shells)
and `sqlite` (database startup).

## Rendering in the browser

The server never renders HTML. A static page shell loads htmx 4, Bulma and the esbuild bundle `hx-hono.js`; its
`#app` element fetches a view endpoint, which returns `{route, vm}` (`UiResponse`). The `hono` htmx extension
renders the template for `route` with `vm` before htmx swaps it in.

- View models are Java records (`*VM`); typescript-generator writes `vm-types.d.ts` from them and from `UiRoute`.
- `routes.ts` maps every `UiRoute` to a template with `satisfies Record<UiRoute, …>`, so `tsc` fails the build
  when a route has no template.
- Mutations answer with the Board envelope too (200, or 400/409 with errors), never 204.

## Time

"Today" comes from an injected `java.time.Clock`. `app-server` produces `Clock.systemDefaultZone()`; tests replace
it with a mutable one. Receivers never pass a date.
