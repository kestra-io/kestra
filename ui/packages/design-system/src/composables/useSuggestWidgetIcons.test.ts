import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {ref, effectScope, nextTick, defineComponent, h} from "vue"
import {useSuggestWidgetIcons, type SuggestWidgetIconsContext} from "./useSuggestWidgetIcons"
import {OVERFLOW_WIDGETS_ID} from "../utils/monacoSetup"
import {STATES} from "../utils/state"

vi.mock("monaco-editor/editor/editor.api", () => ({
    editor: {
        ContentWidgetPositionPreference: {EXACT: 0, ABOVE: 1, BELOW: 2},
    },
}))

// monacoSetup.ts imports this at module level and instantiates Emitter before the editor.api mock is applied.
vi.mock("monaco-editor/languages/features/typescript/register", () => ({
    typescriptDefaults: {setCompilerOptions: vi.fn()},
    ScriptTarget: {ES2020: 7},
}))

let mountedIcons: string[] = []

const RecordingTaskIcon = defineComponent({
    props: {cls: {type: String, default: ""}},
    setup(props) {
        mountedIcons.push(props.cls)
        return () => h("i", {"data-cls": props.cls})
    },
})

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

function makeCtx(overrides: Partial<SuggestWidgetIconsContext> = {}): SuggestWidgetIconsContext {
    return {
        taskIcon: RecordingTaskIcon,
        loadTaskIcon: ref(vi.fn()),
        largeSuggestions: ref(false),
        codeEditor: () => undefined,
        datePicker: {show: vi.fn(), remove: vi.fn()},
        ...overrides,
    }
}

async function flushAll() {
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
}

describe("useSuggestWidgetIcons", () => {
    let scope: ReturnType<typeof effectScope>
    let observeAndResize: () => void
    let teardown: () => void

    beforeEach(() => {
        mountedIcons = []
        scope = effectScope()
        const result = scope.run(() => useSuggestWidgetIcons(makeCtx()))!
        observeAndResize = result.observeAndResize
        teardown = result.teardown
    })

    async function openSuggestWidget() {
        const {contentWidgets} = buildOverflowContainer()
        observeAndResize()

        const widget = buildSuggestWidget()
        contentWidgets.appendChild(widget)
        await flushAll()
        return {contentWidgets, widget}
    }

    afterEach(() => {
        scope.stop()
        vi.restoreAllMocks()
        document.body.innerHTML = ""
    })

    it("injects the plugin icon and hides the VS Code icon for a known plugin FQCN", async () => {
        const {widget} = await openSuggestWidget()

        const row = buildRow("Log", "io.kestra.plugin.core.log", "row-plugin")
        widget.appendChild(row)
        await Promise.resolve()

        expect(row.querySelector(".kestra-icon-wrapper [data-cls='io.kestra.plugin.core.log.Log']")).not.toBeNull()
        expect(mountedIcons).toEqual(["io.kestra.plugin.core.log.Log"])
        expect((row.querySelector(".suggest-icon") as HTMLElement).style.display).toBe("none")
    })

    it("leaves the VS Code icon visible for a label that matches no plugin or state", async () => {
        const {widget} = await openSuggestWidget()

        const row = buildRow("namespace", undefined, "row-unknown")
        widget.appendChild(row)
        await Promise.resolve()

        expect(row.querySelector(".kestra-icon-wrapper")).toBeNull()
        expect(mountedIcons).toEqual([])
        expect((row.querySelector(".suggest-icon") as HTMLElement).style.display).toBe("")
    })

    it("produces exactly one icon wrapper when the same row is processed again", async () => {
        const {widget} = await openSuggestWidget()
        const row = buildRow("Log", "io.kestra.plugin.core.log", "row-dedup")

        widget.appendChild(row)
        await Promise.resolve()
        widget.removeChild(row)
        await Promise.resolve()
        widget.appendChild(row)
        await Promise.resolve()

        expect(row.querySelectorAll(".kestra-icon-wrapper")).toHaveLength(1)
    })

    it("mounts the icon once when one batch of mutations reports the same row twice", async () => {
        const {widget} = await openSuggestWidget()
        const row = buildRow("Log", "io.kestra.plugin.core.log", "row-batch")
        widget.appendChild(row)
        await Promise.resolve()
        mountedIcons = []

        const nameContainer = row.querySelector(".monaco-icon-name-container") as HTMLElement
        nameContainer.appendChild(document.createTextNode(""))
        nameContainer.appendChild(document.createTextNode(""))
        await Promise.resolve()

        expect(mountedIcons).toHaveLength(1)
        expect(row.querySelectorAll(".kestra-icon-wrapper")).toHaveLength(1)
    })

    it("stops decorating the rows of the open suggest widget once torn down", async () => {
        const {widget} = await openSuggestWidget()

        teardown()

        const lateRow = buildRow("Log", "io.kestra.plugin.core.log", "row-late")
        widget.appendChild(lateRow)
        await Promise.resolve()

        expect(lateRow.querySelector(".kestra-icon-wrapper")).toBeNull()
        expect(mountedIcons).toEqual([])
    })

    it("does not pick up a suggest widget opened after teardown", async () => {
        const {contentWidgets} = buildOverflowContainer()
        observeAndResize()

        teardown()

        const widget = buildSuggestWidget()
        widget.appendChild(buildRow("Log", "io.kestra.plugin.core.log", "row-next"))
        contentWidgets.appendChild(widget)
        await flushAll()

        expect(widget.querySelector(".kestra-icon-wrapper")).toBeNull()
        expect(mountedIcons).toEqual([])
    })

    it("does not throw and injects no icons when no overflow container is in the DOM", () => {
        expect(() => observeAndResize()).not.toThrow()
        expect(document.querySelectorAll(".kestra-icon-wrapper")).toHaveLength(0)
    })

    it("ignores nodes added to the overflow container that are not a suggest widget", async () => {
        const {contentWidgets} = buildOverflowContainer()
        observeAndResize()

        const row = buildRow("Log", "io.kestra.plugin.core.log", "row-stray")
        contentWidgets.appendChild(row)
        await flushAll()

        expect(row.querySelector(".kestra-icon-wrapper")).toBeNull()
        expect(mountedIcons).toEqual([])
    })

    it("injects the state icon and hides the VS Code icon for a label that matches an execution state", async () => {
        const {widget} = await openSuggestWidget()

        const row = buildRow(Object.keys(STATES)[0], undefined, "row-state")
        widget.appendChild(row)
        await Promise.resolve()

        expect(row.querySelector(".kestra-icon-wrapper")).not.toBeNull()
        expect(mountedIcons).toEqual([])
        expect((row.querySelector(".suggest-icon") as HTMLElement).style.display).toBe("none")
    })
})
