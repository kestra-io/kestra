# Blocks editor E2E test plan

Persistent Playwright regression suite for the Blocks canvas
(`ui/src/components/no-code/blocks/`), kept long-term to QA every
iteration of this feature — not a one-off report.

## Scope

Everything a keyboard-first user can do on the Blocks tab of the flow
editor: navigating the canvas, inserting every kind of block, editing
every kind of form input, and mutating the tree (duplicate/delete/reorder/
split-view). All flows through real keyboard interaction against a live
dev server and backend, with mutations verified against the YAML the
backend actually persisted — not just the DOM.

## Fixtures

`tests/e2e/fixtures/flows/blocks-canvas.yaml` — one flow exercising every
canvas shape the suite needs to walk and mutate:
- a disabled Schedule trigger
- a `Sequential` flowable with two child tasks and its own empty
  errors/finally lanes (covers step-into/out, nested lanes)
- two top-level leaf tasks
- empty top-level errors/finally sections (covers empty-state insertion)

`tests/e2e/fixtures/flows/blocks-tall.yaml` — sixteen leaf tasks plus an
errors and a finally block, used only by the scrolling spec. Scrolling
behaviour only exists once the canvas overflows the viewport, and the
finally block (`cleanup`) is the last stop on the canvas, which is the
one that cannot be centred.

## Files

| File | Covers |
|---|---|
| `blocks-navigation.spec.ts` | Keyboard-only canvas navigation |
| `blocks-canvas-scroll.spec.ts` | Scrolling: palette-jump centring, the max-scroll boundary, status-bar clearance |
| `blocks-insert.spec.ts` | Every insertion entry point |
| `blocks-edit-forms.spec.ts` | Every generated-form input family |
| `blocks-mutations.spec.ts` | Duplicate, delete, reorder, split view, command menu, save |
| `blocks-dag.spec.ts` | DAG `{task, dependsOn}` wrapper rendering and editing |
| `blocks-after-execution.spec.ts` | The afterExecution section: render, walk, insert, edit, duplicate/delete, command-menu goto |
| `blocks-flow-properties.spec.ts` | The flow properties panel: every flow-level field, per-family edits, disabled tooltip, add-to labels |
| `blocks.helpers.ts` | Shared login/open/ring/insert/save/fetch-YAML helpers |

## Coverage detail

**Navigation** (`blocks-navigation.spec.ts`)
- Forward walk through every stop (trigger → flowable → children → its
  own empty lanes → leaf tasks → empty top-level sections) with wrap-around
- Backward walk with ArrowUp
- Step into a group (ArrowRight) / back to parent (ArrowLeft)
- Collapse/expand a group with ArrowLeft/ArrowRight
- Tab enters the canvas as a single composite stop (roving tabindex) and
  a single Tab exits it entirely; arrows move real DOM focus in lockstep
- Clicking a card syncs the keyboard ring
- Dock-pane focus: ArrowRight/ArrowLeft walks Inputs → Form → back to card
- Escape backs out one level at a time (dock field → panel → gone)
- Help overlay open/close (`?` / Escape)

**Scrolling** (`blocks-canvas-scroll.spec.ts`) — a separate file because it
needs the tall fixture, and a describe block only gets one `beforeEach`:
- A command-palette `Go to` jump centres its destination, asserted as a
  distance from the scrollport centre so neither `nearest` (parks it at the
  edge) nor `start` (pins it to the top) passes
- The max-scroll boundary: jumping to the last block, which cannot be
  centred because less than half a scrollport of content sits beneath it,
  still leaves it fully visible and clear of the status bar
- Arrow-stepping keeps a block near the bottom clear of the status bar —
  stepping stays on `nearest`, so this pins `scroll-padding-bottom`
- Jumping back up leaves the destination fully within the scrollport

**Insertion** (`blocks-insert.spec.ts`)
- Insertion caret shows `⇧A` above / `A` below the focused block
- `a` inserts after the focused block; `Shift+A` inserts before it —
  both round-tripped through save and verified in persisted YAML order
- `/` opens the picker anchored on the focused block
- First insertion into an empty top-level section and into a flowable's
  own empty lane (both persisted-YAML checked)
- Inserting a flowable task and stepping into its newly-created empty
  branch
- Inserting on a focused trigger offers trigger types, not task types
  (regression for a real bug: this used to leak task types into the
  triggers array)
- Command menu insertion, scoped to the currently focused block

**Form editing** (`blocks-edit-forms.spec.ts`) — every generated-form
input family, each verified against persisted YAML:
- Renaming a task's id via the inline Monaco id field, canvas card follows
- Editing a plain text field (message)
- String ⇄ Array segmented toggle
- Enum select (inside the collapsed "Logging" group)
- Boolean switch (inside the collapsed "Execution" group, Form column —
  distinct from the Inputs column's unrelated "Execution context" section)
- Duration field via its preset buttons
- Raw Source-tab YAML edit, canvas syncs from it
- Regression: editing one open tab's Source must never bleed into another
  open tab (they used to share one Monaco model)
- Regression: two tasks sharing the same id keep visually distinct focus
  rings (disambiguated dom ids)

**Mutations & split view** (`blocks-mutations.spec.ts`)
- `d` duplicates the focused block right after it, persisted order checked
- `Backspace` → confirm dialog → delete → focus moves to a neighbor →
  Undo restores the block
- `Backspace` on an empty-section placeholder is a no-op (no dialog)
- `Alt+Arrow` reorders the focused block, persisted order checked
- Split view: each tiled tab gets its own group/tabbar (VSCode editor
  groups), no tab is ever duplicated across tabbars, and a pane can be
  closed independently
- Dragging one pane's only tab onto another pane merges it in, collapsing
  the emptied pane (VSCode editor-group behavior)
- Command menu jumps between sections
- `Ctrl/Cmd+S` saves the draft from the Blocks page itself (this page does
  not mount `NoCode.vue`'s global save handler, so it needs its own)
- A combined duplicate → reorder → delete sequence checked end-to-end
  against both the canvas DOM order and the persisted YAML order

## Running

Against a local dev server + backend for this worktree (not the
Docker-based `start-e2e-tests-backend.sh`, which serves a published
`develop` image that doesn't have this feature):

```bash
cd ui
E2E_BASE_URL=http://localhost:5174 \
E2E_USERNAME=<your dev login> \
E2E_PASSWORD=<your dev password> \
npx playwright test --config=tests/e2e/playwright.config.ts tests/e2e/blocks/
```

Each spec creates its fixture flow via the API in `beforeEach` and deletes
it in `afterEach` (`FlowsApi.generateFlowViaApi` / `removeFlowsViaApi`),
so runs are self-cleaning against whatever backend `E2E_BASE_URL` points
to.

## Bugs found and fixed while writing this suite

- **Task-type picker never confirmed a fresh search with Enter**
  (`BlockEditor.vue`): `pickerFocusedIndex` reset to `-1` on every filter
  change instead of auto-highlighting the top result (unlike the command
  menu, which already did this correctly). A keyboard user typing a
  search and pressing Enter got nothing. Fixed to mirror the command
  menu's default-highlighted-first-item behavior.
- **`a` on a focused trigger offered task types instead of trigger types**
  (`sectionFromParentPath`): anchoring the picker on a `triggers[i]` path
  fell through to the generic "tasks" section. Fixed and covered by a
  regression test.
- **`Ctrl/Cmd+S` was a no-op on the Blocks page**: `NoCode.vue`'s
  `useKeyboardSave()` isn't mounted on this route, so the footer's
  advertised shortcut did nothing. Added a `save` case to
  `dispatchBlockEditorAction`.

## Suite-hardening lessons (2026-07-13)

- **Opening a block lands it as a same-place tab** (the intended default,
  asserted by its own test) — the canvas hides behind its own "No-code" tab.
  Any assertion about the canvas after opening/editing a block must go through
  `backToCanvas()` first; the pre-merge tests assumed a permanently visible
  canvas and rotted silently.
- **Cold-load form re-render swallows fast typing**: on a fresh browser
  context the plugin schema loads after the form first paints; the re-render
  recreates the Monaco fields and DISCARDS anything typed in the gap. A warm
  browser never reproduces it — only fresh test contexts do.
  `replaceMonacoContent()` types, verifies the text landed, and retries;
  app-side, `TaskEdit` now flushes its pending edit on tab deactivation.
- **`saveFlow()` waits out any previous "Successfully saved" toast** before
  saving, otherwise a stale toast satisfies the check while the new save is
  still in flight and the follow-up YAML fetch reads the previous revision.
- **quotas** is advertised by the OSS flow schema but rejected by the OSS
  executor at runtime (EE feature) in a way that poison-pills the queue and
  crash-loops the server on every boot — the flow properties panel
  deliberately does not offer it, and a test pins that.
- **Overlay text matches must be scoped to the overlay.** The canvas renders
  block labels carrying the same words as menu entries, and a card sitting
  behind an overlay still looks visible to Playwright. `pickTask()` and
  `goToSectionViaPalette()` both scope their lookup to the picker's listbox
  and the command menu respectively.

## Known gaps / follow-ups

- Coverage is Log/Sequential/If/Schedule/Webhook/Fail task types plus the
  generated-form input families they exercise (text, array, enum,
  boolean, duration, raw source). Plugin-specific input widgets outside
  those families (e.g. file upload, code-editor-typed properties) are not
  yet covered.
- No visual-regression (screenshot diff) coverage — this suite asserts
  behavior and persisted YAML, not pixels.
- **Dev-server timing sensitivity**: a couple of interactions (opening
  the task-edit dock, switching to the Source tab, expanding an accordion
  group) trigger an async re-render that briefly recreates the Monaco
  editor instances. `blocks.helpers.ts`'s `waitForMonacoStable()` guards
  against this by polling the editor count until it settles — if a new
  test adds a Monaco interaction after a tab switch or accordion expand,
  call it first.
- **Possible robustness gap, not fully root-caused**: typing a full block
  replacement into the Source tab character-by-character (as a real
  keyboard user would with individual keystrokes, rather than a single
  paste) produces many transient invalid-YAML intermediate states. In one
  observed run this cascaded into an uncaught `YAMLException` storm, a
  batch of 404s, and the whole app navigating to a "Page not found" route
  — though it did not reproduce on repeated attempts. The suite avoids
  the flake by using `page.keyboard.insertText(...)` (atomic paste,
  matches how a user would realistically replace a whole block) instead
  of `page.keyboard.type(...)` for Source-tab edits, but the underlying
  crash risk under rapid partial-YAML keystrokes has not been fixed and
  is worth a dedicated look.
