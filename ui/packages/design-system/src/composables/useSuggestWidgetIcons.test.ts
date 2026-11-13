import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {ref, effectScope, nextTick} from "vue"
import {useSuggestWidgetIcons, type SuggestWidgetIconsContext} from "./useSuggestWidgetIcons"
import {OVERFLOW_WIDGETS_ID} from "../utils/monacoSetup"
import {STATES} from "../utils/state"

vi.mock("monaco-editor/editor/editor.api", () => ({
    editor: {
        ContentWidgetPositionPreference: {EXACT: 0, ABOVE: 1, BELOW: 2},
    },
}))

// monacoSetup.ts imports monaco-editor/languages/features/typescript/register at module level,
// which instantiates Emitter before the editor.api mock is applied; stub it out.
vi.mock("monaco-editor/languages/features/typescript/register", () => ({
    typescriptDefaults: {setCompilerOptions: vi.fn()},
    ScriptTarget: {ES2020: 7},
}))

// ── DOM helpers ──────────────────────────────────────────────────────────────

function buildOverflowContainer(): {contentWidgets: HTMLElement} {
    const overflowNode = document.createElement("div")
    overflowNode.id = OVERFLOW_WIDGETS_ID
    const contentWidgets = document.createElement("div")
    contentWidgets.className = "overflowingContentWidgets"
    overflowNode.appendChild(contentWidgets)
    document.body.appendChild(overflowNode)
    return {contentWidgets}
}

function buildSuggestWidget(): HTMLElement {
    const widget = document.createElement("div")
    widget.className = "suggest-widget"
    // The sash element is required: observeAndResize reads .monaco-sash.vertical
    const sash = document.createElement("div")
    sash.className = "monaco-sash vertical disabled"
    widget.appendChild(sash)
    return widget
}

function buildRow(label: string, pkg?: string, id = "row-1"): HTMLElement {
    const row = document.createElement("div")
    row.className = "monaco-list-row"
    row.id = id
    row.setAttribute("data-index", "0")

    const icon = document.createElement("span")
    icon.className = "suggest-icon"
    row.appendChild(icon)

    const nameContainer = document.createElement("span")
    nameContainer.className = "monaco-icon-name-container"
    nameContainer.textContent = label
    row.appendChild(nameContainer)

    if (pkg) {
        const details = document.createElement("span")
        details.className = "details-label"
        details.textContent = pkg
        row.appendChild(details)
    }

    return row
}

// ── Context factory ───────────────────────────────────────────────────────────

function makeCtx(overrides: Partial<SuggestWidgetIconsContext> = {}): SuggestWidgetIconsContext {
    return {
        taskIcon: {render: () => null} as unknown as SuggestWidgetIconsContext["taskIcon"],
        loadTaskIcon: ref(vi.fn()),
        largeSuggestions: ref(false),
        codeEditor: () => undefined,
        datePicker: {show: vi.fn(), remove: vi.fn()},
        ...overrides,
    }
}

// ── Flush helper: MutationObserver callbacks fire synchronously in jsdom, ─────
// but Vue's watch() schedules on the microtask queue, so we need multiple
// ticks to let the watch handler run and then the rowObserver to be set up.

async function flushAll() {
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
}

// ── Shared setup ──────────────────────────────────────────────────────────────

describe("useSuggestWidgetIcons", () => {
    let scope: ReturnType<typeof effectScope>
    let observeAndResize: () => void
    let teardown: () => void

    beforeEach(() => {
        scope = effectScope()
        const result = scope.run(() => useSuggestWidgetIcons(makeCtx()))!
        observeAndResize = result.observeAndResize
        teardown = result.teardown
    })

    afterEach(() => {
        scope.stop()
        vi.restoreAllMocks()
        document.body.innerHTML = ""
    })

    // ── 1. Known plugin gets its icon ─────────────────────────────────────────

    it("injects a kestra-icon-wrapper and hides the VS Code icon for a known plugin FQCN", async () => {
        const {contentWidgets} = buildOverflowContainer()
        observeAndResize()

        const widget = buildSuggestWidget()
        contentWidgets.appendChild(widget)
        await flushAll()

        // The label is the last segment, the package is the full parent namespace
        const row = buildRow("Log", "io.kestra.plugin.core.log", "row-plugin")
        widget.appendChild(row)
        await Promise.resolve()

        expect(row.querySelector(".kestra-icon-wrapper")).not.toBeNull()
        expect((row.querySelector(".suggest-icon") as HTMLElement).style.display).toBe("none")
    })

    // ── 2. Unknown label is left untouched ────────────────────────────────────

    it("leaves the VS Code icon visible for a label that matches no plugin or state", async () => {
        const {contentWidgets} = buildOverflowContainer()
        observeAndResize()

        const widget = buildSuggestWidget()
        contentWidgets.appendChild(widget)
        await flushAll()

        const row = buildRow("namespace", undefined, "row-unknown")
        widget.appendChild(row)
        await Promise.resolve()

        expect(row.querySelector(".kestra-icon-wrapper")).toBeNull()
        expect((row.querySelector(".suggest-icon") as HTMLElement).style.display).toBe("")
    })

    // ── 3. Icon is not injected twice for the same entry ──────────────────────

    it("produces exactly one kestra-icon-wrapper even when the same row is processed twice", async () => {
        const {contentWidgets} = buildOverflowContainer()
        observeAndResize()

        const widget = buildSuggestWidget()
        contentWidgets.appendChild(widget)
        await flushAll()

        const row = buildRow("Log", "io.kestra.plugin.core.log", "row-dedup")

        // First append: row is processed
        widget.appendChild(row)
        await Promise.resolve()

        // Remove and re-append to trigger a second MutationObserver callback
        widget.removeChild(row)
        await Promise.resolve()
        widget.appendChild(row)
        await Promise.resolve()

        expect(row.querySelectorAll(".kestra-icon-wrapper")).toHaveLength(1)
    })

    // ── 4. Observer is disconnected on teardown ───────────────────────────────

    it("disconnects both observers when teardown is called", async () => {
        const {contentWidgets} = buildOverflowContainer()
        observeAndResize()

        // Bring the suggest widget up so the rowObserver is created too
        const widget = buildSuggestWidget()
        contentWidgets.appendChild(widget)
        await flushAll()

        const disconnectSpy = vi.spyOn(MutationObserver.prototype, "disconnect")

        teardown()

        expect(disconnectSpy).toHaveBeenCalled()
    })

    // ── 5. No suggest widget present is a no-op ───────────────────────────────

    it("does not throw and injects no icons when no overflow container is in the DOM", () => {
        // Document is empty (afterEach clears it; no buildOverflowContainer call here)
        expect(() => observeAndResize()).not.toThrow()
        expect(document.querySelectorAll(".kestra-icon-wrapper")).toHaveLength(0)
    })

    // ── Bonus: state label gets its icon ──────────────────────────────────────

    it("injects a kestra-icon-wrapper for a label that matches a known execution state", async () => {
        const stateKey = Object.keys(STATES)[0]

        const {contentWidgets} = buildOverflowContainer()
        observeAndResize()

        const widget = buildSuggestWidget()
        contentWidgets.appendChild(widget)
        await flushAll()

        const row = buildRow(stateKey, undefined, "row-state")
        widget.appendChild(row)
        await Promise.resolve()

        expect(row.querySelector(".kestra-icon-wrapper")).not.toBeNull()
        expect((row.querySelector(".suggest-icon") as HTMLElement).style.display).toBe("none")
    })
})
