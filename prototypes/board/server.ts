// PROTOTYPE, throwaway. Answers "Board look and behaviour" (issue #5).
// Three variants of the calendar Board on one route, switchable via ?variant=A|B|C and the bottom bar:
//   A  Scroll strip   15 Columns side by side, horizontal scroll, Today pinned left
//   B  Two weeks      two rows of 7 day Columns, Future as a sidebar
//   C  Focus+agenda   wide Today, the next 13 days as a vertical agenda, Future on the right
// All state is in memory. Deviation from the real stack: Hono's html templates render on the
// server here instead of in the browser via the `hono` htmx extension; the look and the
// request/response shape (every mutation returns the whole app, swapped with innerMorph) are the same.
// Run: bun install && bun run start  ->  http://localhost:3005
import { Hono } from 'hono'
import { html, raw } from 'hono/html'
import type { HtmlEscapedString } from 'hono/utils/html'

type H = HtmlEscapedString | Promise<HtmlEscapedString>

// ---------- dates (ISO yyyy-mm-dd, UTC arithmetic) ----------
const D = (s: string) => new Date(s + 'T00:00:00Z')
const iso = (d: Date) => d.toISOString().slice(0, 10)
const add = (s: string, n: number) => { const d = D(s); d.setUTCDate(d.getUTCDate() + n); return iso(d) }
const dow = (s: string) => (D(s).getUTCDay() + 6) % 7 // Mon=0
const diff = (a: string, b: string) => Math.round((+D(b) - +D(a)) / 864e5)
const fmt = (s: string) => D(s).toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' })
const short = (s: string) => D(s).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' })
const realToday = () => new Date().toLocaleDateString('sv')
const WD = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const SPAN = 14

// ---------- state ----------
type Schedule =
  | { kind: 'weekly'; weekdays: number[]; every: number }
  | { kind: 'monthly'; day: number }
  | { kind: 'interval'; n: number; unit: 'days' | 'weeks' }
type RT = { id: number; title: string; notes: string; sched: Schedule; from: string; stopped: boolean }
type Task = { id: number; title: string; notes: string; due: string; orig: string; done: boolean; rank: number; rt?: number }

let today = realToday()
let seq = 1, rankSeq = 1
let tasks: Task[] = []
let rts: RT[] = []
let futureAll = false

const spanEnd = () => add(today, SPAN - 1)
const rtOf = (t: Task) => rts.find(r => r.id === t.rt)
const newTask = (p: Partial<Task> & { title: string; due: string }) => {
  const t: Task = { id: seq++, notes: '', orig: p.due, done: false, rank: rankSeq++, ...p }
  tasks.push(t); return t
}

function produces(r: RT, d: string): boolean {
  if (d < r.from) return false
  const s = r.sched
  if (s.kind === 'weekly') {
    const monday = add(r.from, -dow(r.from))
    return s.weekdays.includes(dow(d)) && Math.floor(diff(monday, d) / 7) % s.every === 0
  }
  if (s.kind === 'monthly') return D(d).getUTCDate() === s.day
  return false
}

// Calendar Schedules: every date within the span plus exactly one beyond; adopt an open Occurrence already there.
function generate() {
  for (const r of rts) {
    if (r.stopped || r.sched.kind === 'interval') continue
    let beyond = false
    for (let d = r.from, i = 0; i < 800 && !beyond; d = add(d, 1), i++) {
      if (!produces(r, d)) continue
      if (d > spanEnd()) beyond = true
      const mine = tasks.filter(t => t.rt === r.id)
      if (mine.some(t => t.orig === d || (!t.done && t.due === d))) continue
      newTask({ title: r.title, due: d, rt: r.id })
    }
  }
}

const isStale = (t: Task) => {
  const r = rtOf(t)
  if (!r || t.done || t.due <= today) return false
  return r.stopped || (r.sched.kind !== 'interval' && !produces(r, t.orig))
}
const isOverdue = (t: Task) => !t.done && t.due < today
const titleOf = (t: Task) => rtOf(t)?.title ?? t.title
const notesOf = (t: Task) => rtOf(t)?.notes ?? t.notes

function seed() {
  today = realToday(); seq = 1; rankSeq = 1; tasks = []; rts = []; futureAll = false
  const t = (o: number, title: string, extra: Partial<Task> = {}) => newTask({ title, due: add(today, o), ...extra })
  t(-3, 'Renew passport photo appointment')
  t(-1, 'Reply to landlord', { notes: 'About the radiator in the bedroom.' })
  t(0, 'Buy birthday present for Anna', { notes: 'Book or a nice pen?' })
  t(0, 'Call the dentist')
  t(0, 'Take out the recycling', { done: true })
  t(1, 'Prepare slides for Tuesday', { orig: add(today, -2) })
  t(2, 'Return library books')
  t(4, 'Fix bike tyre')
  t(4, 'Book train to Basel', { notes: 'Check Sparpreis first.' })
  t(9, 'Car service')
  for (const [o, title] of [[16, 'Tax return'], [20, 'Dentist check-up'], [23, 'Renew insurance'], [30, 'Plan holiday'], [41, 'Winter tyres'], [55, 'Annual review'], [70, 'Clean gutters']] as const) t(o, title)
  // weekly Mon + Thu, started three weeks ago; past ones done
  rts.push({ id: 1, title: 'Weekly review', notes: 'Inbox zero, plan next week.', sched: { kind: 'weekly', weekdays: [0, 3], every: 1 }, from: add(today, -21), stopped: false })
  // monthly on the 1st
  rts.push({ id: 2, title: 'Pay rent', notes: '', sched: { kind: 'monthly', day: 1 }, from: add(today, -40), stopped: false })
  // every second Saturday; was Fridays before an edit -> one Stale Occurrence left behind
  rts.push({ id: 3, title: 'Gym: long session', notes: '', sched: { kind: 'weekly', weekdays: [5], every: 2 }, from: today, stopped: false })
  const fri = add(today, ((4 - dow(today)) + 7) % 7 || 7)
  newTask({ title: 'Gym: long session', due: fri, rt: 3 })
  // interval: 3 days after last done
  rts.push({ id: 4, title: 'Water the plants', notes: '', sched: { kind: 'interval', n: 3, unit: 'days' }, from: today, stopped: false })
  newTask({ title: 'Water the plants', due: add(today, -1), rt: 4 })
  generate()
  for (const x of tasks) if (x.rt && x.due < add(today, -1)) x.done = true // pretend the past was handled...
  const missed = tasks.find(x => x.rt === 1 && x.due < today && x.due >= add(today, -7)) // ...except one weekly review
  if (missed) missed.done = false
  for (const x of tasks) if (x.rt === 4) x.done = false
}
seed()

// ---------- view model ----------
function board() {
  generate()
  const days = Array.from({ length: SPAN }, (_, i) => {
    const date = add(today, i)
    const cards = tasks.filter(t => t.due === date && !(i === 0 && false)).sort((a, b) => a.rank - b.rank)
    return { date, i, cards, overdue: i === 0 ? tasks.filter(isOverdue).sort((a, b) => a.due.localeCompare(b.due) || a.rank - b.rank) : [] }
  })
  const fut = tasks.filter(t => t.due > spanEnd() && !t.done).sort((a, b) => a.due.localeCompare(b.due) || a.rank - b.rank)
  return { days, future: futureAll ? fut : fut.slice(0, 5), futureTotal: fut.length }
}
type Day = ReturnType<typeof board>['days'][number]
type Mode = 'full' | 'compact' | 'row'

// ---------- shared pieces ----------
function card(t: Task, mode: Mode, inFuture = false): H {
  const r = rtOf(t), od = isOverdue(t), st = isStale(t), moved = t.orig !== t.due
  const c = mode !== 'full'
  const markers = html`
    ${inFuture ? html`<span class="tag is-light">${fmt(t.due)}</span>` : ''}
    ${od ? (c ? html`<span class="mk od" title="Overdue since ${fmt(t.due)}">⚠ ${short(t.due)}</span>` : html`<span class="tag is-danger is-light">Overdue · ${fmt(t.due)}</span>`) : ''}
    ${r ? html`<span class="mk" title="Recurring: ${schedText(r.sched)}">↻${c ? '' : ' ' + schedText(r.sched)}</span>` : ''}
    ${moved ? html`<span class="mk" title="Original due date ${fmt(t.orig)}">${c ? '⇢' : 'orig.'} ${short(t.orig)}</span>` : ''}
    ${st ? html`<span class="tag is-warning ${c ? 'is-small' : ''}" title="Its Schedule no longer produces ${fmt(t.orig)}${r?.stopped ? ' (Stopped)' : ''}. Tick it off or move it.">Stale</span>` : ''}`
  return html`
  <div class="tcard ${mode} ${t.done ? 'is-done' : ''} ${od ? 'is-overdue' : ''} ${st ? 'is-stale' : ''}" id="t-${t.id}">
    <input type="hidden" name="taskId" value="${t.id}">
    <button type="button" class="tick" hx-post="/tasks/${t.id}/toggle" title="${t.done ? 'Untick' : 'Tick off'}">${t.done ? '☑' : '☐'}</button>
    <div class="body">
      <div class="ttl">${titleOf(t)}</div>
      <div class="meta">${markers}</div>
      ${mode === 'full' && notesOf(t) ? html`<div class="notes">${notesOf(t)}</div>` : ''}
    </div>
    <div class="acts">
      ${t.done ? '' : html`
        ${od ? html`<button type="button" hx-post="/tasks/${t.id}/shift?to=today" title="Move to Today">→Today</button>` : ''}
        <button type="button" hx-post="/tasks/${t.id}/shift?days=1" title="One day later">+1d</button>
        <button type="button" hx-post="/tasks/${t.id}/shift?days=7" title="One week later">+1w</button>`}
      <button type="button" hx-get="/tasks/${t.id}/edit" title="Edit">✎</button>
    </div>
  </div>`
}

const dayLabel = (d: Day) => d.i === 0 ? 'Today' : d.i === 1 ? 'Tomorrow' : WD[dow(d.date)]
const openCount = (d: Day) => d.cards.filter(t => !t.done).length + d.overdue.length

function dayList(d: Day, mode: Mode): H {
  return html`
    ${d.i === 0 && d.overdue.length ? html`
      <div class="overdue-list" id="overdue" data-sortable data-put="false" data-sort="false" title="Overdue: shown in Today, due date unchanged">
        ${d.overdue.map(t => card(t, mode))}
      </div>` : ''}
    <form class="cards" id="cards-${d.date}" data-sortable hx-post="/columns/${d.date}" hx-trigger="add, update">
      ${d.cards.map(t => card(t, mode))}
    </form>`
}

const quickAdd = (date: string) => html`
  <form class="quick" id="quick-${date}" hx-post="/quick" hx-on:htmx:after:request="this.reset()">
    <input type="hidden" name="due" value="${date}">
    <input class="input is-small" name="title" placeholder="+ add" autocomplete="off">
  </form>`

function dayColumn(d: Day, mode: Mode, cls = ''): H {
  const weekend = dow(d.date) >= 5
  return html`
  <section class="col ${cls} ${d.i === 0 ? 'is-today' : ''} ${weekend ? 'is-weekend' : ''}" id="col-${d.date}">
    <header class="colhead">
      <span class="day">${dayLabel(d)}</span> <span class="date">${short(d.date)}</span>
      <span class="count">${openCount(d) || ''}</span>
      <button type="button" class="plus" hx-get="/tasks/new?due=${d.date}" title="New task on ${fmt(d.date)}">+</button>
    </header>
    ${dayList(d, mode)}
    ${quickAdd(d.date)}
  </section>`
}

function futureColumn(b: ReturnType<typeof board>, mode: Mode, cls = ''): H {
  const more = b.futureTotal - 5
  return html`
  <section class="col future ${cls}" id="col-future">
    <header class="colhead"><span class="day">Future</span> <span class="date">from ${short(add(spanEnd(), 1))}</span>
      <span class="count">${b.futureTotal || ''}</span>
      <button type="button" class="plus" hx-get="/tasks/new?due=${add(spanEnd(), 1)}" title="New task in Future">+</button>
    </header>
    <form class="cards" id="cards-future" data-sortable data-sort="false" hx-post="/future" hx-trigger="add" title="Drop here: due ${fmt(add(spanEnd(), 1))}">
      ${b.future.map(t => card(t, mode, true))}
    </form>
    ${more > 0 || futureAll ? html`<button type="button" class="button is-small is-ghost" hx-post="/future/toggle">${futureAll ? 'show fewer' : `+${more} more`}</button>` : ''}
  </section>`
}

// ---------- variants ----------
const variants: Record<string, { name: string; render: (b: ReturnType<typeof board>) => H }> = {
  A: {
    name: 'Scroll strip',
    render: b => html`<div class="v-a">${b.days.map(d => dayColumn(d, 'compact'))}${futureColumn(b, 'compact')}</div>`,
  },
  B: {
    name: 'Two weeks',
    render: b => html`<div class="v-b">
      <div class="weeks">
        <div class="week">${b.days.slice(0, 7).map(d => dayColumn(d, 'compact'))}</div>
        <div class="week">${b.days.slice(7).map(d => dayColumn(d, 'compact'))}</div>
      </div>
      ${futureColumn(b, 'compact')}
    </div>`,
  },
  C: {
    name: 'Focus + agenda',
    render: b => html`<div class="v-c">
      ${dayColumn(b.days[0], 'full', 'focus')}
      <section class="agenda">
        ${b.days.slice(1).map(d => html`
          <div class="aday ${dow(d.date) >= 5 ? 'is-weekend' : ''} ${dow(d.date) === 0 ? 'is-monday' : ''}" id="col-${d.date}">
            <div class="ahead"><b>${dayLabel(d)}</b> ${short(d.date)}
              <button type="button" class="plus" hx-get="/tasks/new?due=${d.date}" title="New task on ${fmt(d.date)}">+</button></div>
            <div class="alist">${dayList(d, 'row')}</div>
          </div>`)}
      </section>
      ${futureColumn(b, 'row')}
    </div>`,
  },
}

// ---------- modal (create / edit) ----------
function schedText(s: Schedule): string {
  if (s.kind === 'weekly') return `${s.every > 1 ? `every ${s.every} weeks` : 'weekly'} on ${s.weekdays.map(w => WD[w]).join(', ')}`
  if (s.kind === 'monthly') return `monthly on the ${s.day}.`
  return `${s.n} ${s.unit} after done`
}

function schedFields(key: string, r?: RT, due?: string): H {
  const s = r?.sched
  const kind = !r ? 'oneoff' : s!.kind === 'interval' ? 'interval' : 'calendar'
  const wk = s?.kind === 'weekly' ? s : { weekdays: [dow(due ?? today)], every: 1 }
  return html`
  <div class="sched" data-kind="${kind}" data-cal="${s?.kind === 'monthly' ? 'monthly' : 'weekly'}"
       hx-post="/schedule/preview" hx-trigger="change, load" hx-target="#preview-${key}" hx-swap="innerHTML">
    ${r ? html`<input type="hidden" name="kind" value="${kind}">` : html`
    <div class="field"><div class="buttons has-addons kinds" onchange="this.closest('.sched').dataset.kind=event.target.value">
      ${['oneoff:One-off', 'calendar:Repeats on a calendar', 'interval:Repeats after done'].map(x => { const [v, l] = x.split(':'); return html`
      <label class="button is-small"><input type="radio" name="kind" value="${v}" ${v === kind ? 'checked' : ''}> ${l}</label>` })}
    </div></div>`}
    <div class="only-calendar box is-shadowless">
      <div class="field"><div class="select is-small"><select name="cal" onchange="this.closest('.sched').dataset.cal=this.value">
        <option value="weekly" ${s?.kind !== 'monthly' ? 'selected' : ''}>Weekly</option>
        <option value="monthly" ${s?.kind === 'monthly' ? 'selected' : ''}>Monthly</option></select></div></div>
      <div class="only-weekly">
        <div class="field">every <input class="input is-small num" type="number" min="1" name="every" value="${wk.every}"> week(s) on</div>
        <div class="field wds">${WD.map((w, i) => html`<label class="checkbox"><input type="checkbox" name="wd" value="${i}" ${wk.weekdays.includes(i) ? 'checked' : ''}> ${w}</label> `)}</div>
      </div>
      <div class="only-monthly field">on day <input class="input is-small num" type="number" min="1" max="28" name="mday" value="${s?.kind === 'monthly' ? s.day : D(due ?? today).getUTCDate()}"> of the month</div>
    </div>
    <div class="only-interval box is-shadowless field">
      next one <input class="input is-small num" type="number" min="1" name="n" value="${s?.kind === 'interval' ? s.n : 1}">
      <span class="select is-small"><select name="unit">
        <option ${s?.kind === 'interval' && s.unit === 'days' ? 'selected' : ''}>days</option>
        <option ${s?.kind !== 'interval' || s.unit === 'weeks' ? 'selected' : ''}>weeks</option></select></span>
      after the previous one is done
    </div>
    <p class="help preview" id="preview-${key}"></p>
  </div>`
}

function modal(title: string, body: H, key: string): H {
  return html`
  <div class="modal is-active" id="modal-${key}">
    <div class="modal-background" onclick="this.closest('.modal').remove()"></div>
    <div class="modal-card">
      <header class="modal-card-head"><p class="modal-card-title">${title}</p>
        <button type="button" class="delete" onclick="this.closest('.modal').remove()"></button></header>
      <section class="modal-card-body">${body}</section>
    </div>
  </div>`
}

const textFields = (key: string, title = '', notes = '') => html`
  <div class="field"><input class="input" name="title" value="${title}" placeholder="Title" required autofocus id="title-${key}"></div>
  <div class="field"><textarea class="textarea is-small" rows="2" name="notes" placeholder="Notes (optional)">${notes}</textarea></div>`

function newModal(due: string): H {
  const key = 'new-' + seq + '-' + due
  return modal('New task', html`
    <form hx-post="/tasks">
      ${textFields(key)}
      <div class="field"><label class="label is-small">Due date <span class="has-text-grey">(first due, for a Recurring Task)</span></label>
        <input class="input is-small" type="date" name="due" value="${due}" required></div>
      ${schedFields(key, undefined, due)}
      <button class="button is-primary">Create</button>
    </form>`, key)
}

function editModal(t: Task): H {
  const r = rtOf(t), key = 'edit-' + t.id
  return modal(r ? 'Edit Occurrence' : 'Edit task', html`
    <form hx-post="/tasks/${t.id}">
      ${textFields(key, titleOf(t), notesOf(t))}
      ${r ? html`<p class="help mb-2">Title and notes belong to the Recurring Task and apply to all its Occurrences.</p>` : ''}
      <div class="field"><label class="label is-small">Due date</label>
        <input class="input is-small" type="date" name="due" value="${t.due}" required>
        ${t.orig !== t.due ? html`<p class="help">Original due date: ${fmt(t.orig)}</p>` : ''}</div>
      ${r ? html`
        <hr><h3 class="title is-6">Recurring Task ${r.stopped ? html`<span class="tag is-dark">Stopped</span>` : ''}</h3>
        ${r.stopped ? html`<p>${schedText(r.sched)}</p>` : schedFields(key, r)}` : ''}
      <div class="buttons mt-3">
        <button class="button is-primary">Save</button>
        ${r && !r.stopped ? html`<button type="button" class="button is-danger is-outlined" hx-post="/recurring/${r.id}/stop"
            title="No further Occurrences; open future ones become Stale">Stop Recurring Task</button>` : ''}
      </div>
    </form>`, key)
}

// ---------- page ----------
const variantOf = (c: any) => {
  const u = c.req.header('HX-Current-URL') ?? c.req.url
  const v = new URL(u, 'http://x').searchParams.get('variant') ?? 'A'
  return variants[v] ? v : 'A'
}

function app(v: string, extra: H | '' = ''): H {
  const b = board()
  return html`
  <div class="topbar">
    <b>Board</b> <span class="has-text-grey">PROTOTYPE · ${fmt(today)} ·
    <span title="Simulate time passing">today =</span></span>
    <button type="button" class="button is-small" hx-post="/dev/advance?days=1">+1 day</button>
    <button type="button" class="button is-small" hx-post="/dev/advance?days=7">+1 week</button>
    <button type="button" class="button is-small is-light" hx-post="/dev/reset">reset</button>
    <span class="legend has-text-grey">☐ tick · drag = reschedule / reorder · ⚠ Overdue · ↻ recurring · ⇢ original due date · Stale</span>
  </div>
  ${variants[v].render(b)}
  ${extra}`
}

function page(v: string): H {
  const keys = Object.keys(variants), i = keys.indexOf(v)
  const prev = keys[(i + keys.length - 1) % keys.length], next = keys[(i + 1) % keys.length]
  return html`<!doctype html>
<html data-theme="light"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Board prototype</title>
<link rel="stylesheet" href="/vendor/bulma.css"><link rel="stylesheet" href="/board.css">
<script src="/vendor/htmx.js"></script><script src="/vendor/sortable.js"></script>
</head>
<body hx-target:inherited="#app" hx-swap:inherited="innerMorph">
<main id="app">${app(v)}</main>
<nav class="proto-switcher">
  <a href="?variant=${prev}" id="prev">‹</a><span>${v} (${variants[v].name})</span><a href="?variant=${next}" id="next">›</a>
</nav>
<script>${raw(clientJs)}</script>
</body></html>`
}

const clientJs = `
function initSortables() {
  document.querySelectorAll('[data-sortable]').forEach(el => {
    if (el._sortable) return
    el._sortable = Sortable.create(el, {
      group: { name: 'board', put: el.dataset.put !== 'false' },
      sort: el.dataset.sort !== 'false',
      draggable: '.tcard', filter: '.is-done', preventOnFilter: false,
      animation: 120, ghostClass: 'ghost', emptyInsertThreshold: 24, forceFallback: true, fallbackTolerance: 4,
    })
  })
}
document.addEventListener('DOMContentLoaded', initSortables)
document.addEventListener('htmx:after:settle', initSortables)
document.addEventListener('htmx:after:swap', initSortables)
document.addEventListener('keydown', e => {
  if (e.target.closest('input, textarea, select, [contenteditable]')) return
  if (e.key === 'ArrowLeft') document.getElementById('prev').click()
  if (e.key === 'ArrowRight') document.getElementById('next').click()
  if (e.key === 'Escape') document.querySelector('.modal')?.remove()
})`

// ---------- server ----------
const h = new Hono()
const reply = (c: any, extra: H | '' = '') => c.html(app(variantOf(c), extra))
const ids = (v: unknown) => (Array.isArray(v) ? v : v == null ? [] : [v]).map(Number)
const find = (id: string | number) => tasks.find(t => t.id === Number(id))!

h.get('/', c => c.html(page(variantOf(c))))
h.get('/vendor/htmx.js', () => new Response(Bun.file('node_modules/htmx.org/dist/htmx.js')))
h.get('/vendor/sortable.js', () => new Response(Bun.file('node_modules/sortablejs/Sortable.min.js')))
h.get('/vendor/bulma.css', () => new Response(Bun.file('node_modules/bulma/css/bulma.min.css')))
h.get('/board.css', () => new Response(Bun.file('board.css')))

// drop into / reorder within a day: the Column posts its whole ordered id list
h.post('/columns/:date', async c => {
  const date = c.req.param('date'), body = await c.req.parseBody({ all: true })
  for (const id of ids(body.taskId)) { const t = find(id); if (!t.done) t.due = date; t.rank = rankSeq++ }
  return reply(c)
})
h.post('/future', async c => {
  const body = await c.req.parseBody({ all: true })
  for (const id of ids(body.taskId)) { const t = find(id); if (t.due <= spanEnd()) { t.due = add(spanEnd(), 1); t.rank = rankSeq++ } }
  return reply(c)
})
h.post('/future/toggle', c => { futureAll = !futureAll; return reply(c) })
h.post('/tasks/:id/toggle', c => {
  const t = find(c.req.param('id')); t.done = !t.done
  const r = rtOf(t)
  if (t.done && r && !r.stopped && r.sched.kind === 'interval' && !tasks.some(x => x.rt === r.id && !x.done)) {
    const s = r.sched
    newTask({ title: r.title, due: add(today, s.n * (s.unit === 'weeks' ? 7 : 1)), rt: r.id })
  }
  return reply(c)
})
h.post('/tasks/:id/shift', c => {
  const t = find(c.req.param('id')), to = c.req.query('to'), days = Number(c.req.query('days') ?? 0)
  t.due = to === 'today' ? today : add(t.due < today ? today : t.due, days); t.rank = rankSeq++
  return reply(c)
})
h.post('/quick', async c => {
  const b = await c.req.parseBody()
  if (String(b.title).trim()) newTask({ title: String(b.title).trim(), due: String(b.due) })
  return reply(c)
})
h.get('/tasks/new', c => reply(c, newModal(c.req.query('due') ?? today)))
h.get('/tasks/:id/edit', c => reply(c, editModal(find(c.req.param('id')))))

function parseSched(b: Record<string, any>): Schedule | undefined {
  if (b.kind === 'interval') return { kind: 'interval', n: Math.max(1, Number(b.n)), unit: b.unit === 'days' ? 'days' : 'weeks' }
  if (b.kind !== 'calendar') return undefined
  if (b.cal === 'monthly') return { kind: 'monthly', day: Math.min(28, Math.max(1, Number(b.mday))) }
  const wd = ids(b.wd)
  return { kind: 'weekly', weekdays: wd.length ? wd : [0], every: Math.max(1, Number(b.every)) }
}
h.post('/schedule/preview', async c => {
  const b = await c.req.parseBody({ all: true }), s = parseSched(b)
  if (!s) return c.html('')
  if (s.kind === 'interval') return c.html(html`First due ${fmt(String(b.due ?? today))}; each next one ${schedText(s)}.`)
  const r: RT = { id: 0, title: '', notes: '', sched: s, from: String(b.due ?? today), stopped: false }
  const out: string[] = []
  for (let d = r.from, i = 0; out.length < 5 && i < 400; d = add(d, 1), i++) if (produces(r, d)) out.push(fmt(d))
  return c.html(html`${schedText(s)} → ${out.join(' · ')} …`)
})
h.post('/tasks', async c => {
  const b = await c.req.parseBody({ all: true }), s = parseSched(b)
  const title = String(b.title).trim(), notes = String(b.notes ?? ''), due = String(b.due || today)
  if (!s) newTask({ title, notes, due })
  else {
    const r: RT = { id: rts.length + 1, title, notes, sched: s, from: due, stopped: false }
    rts.push(r)
    if (s.kind === 'interval') newTask({ title, due, rt: r.id })
  }
  return reply(c)
})
h.post('/tasks/:id', async c => {
  const t = find(c.req.param('id')), b = await c.req.parseBody({ all: true }), r = rtOf(t)
  const due = String(b.due || t.due)
  if (due !== t.due) { t.due = due; t.rank = rankSeq++ }
  if (r) {
    r.title = String(b.title).trim(); r.notes = String(b.notes ?? '')
    const s = parseSched(b)
    if (s && !r.stopped && JSON.stringify(s) !== JSON.stringify(r.sched)) { r.sched = s; r.from = today }
  } else { t.title = String(b.title).trim(); t.notes = String(b.notes ?? '') }
  return reply(c)
})
h.post('/recurring/:id/stop', c => { const r = rts.find(x => x.id === Number(c.req.param('id')))!; r.stopped = true; return reply(c) })
h.post('/dev/advance', c => { today = add(today, Number(c.req.query('days') ?? 1)); return reply(c) })
h.post('/dev/reset', c => { seed(); return reply(c) })

export default { port: 3005, fetch: h.fetch }
