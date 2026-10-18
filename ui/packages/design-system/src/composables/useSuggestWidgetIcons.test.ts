import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {ref, h, defineComponent} from "vue"
import {useSuggestWidgetIcons, type SuggestWidgetIconsContext} from "./useSuggestWidgetIcons"
import {OVERFLOW_WIDGETS_ID} from "../utils/monacoSetup"

const MockTaskIcon = defineComponent({
    name: "MockTaskIcon",
    props: ["cls", "onlyIcon", "loadIcon"],
    render() {
        return h("span", {class: "mock-task-icon", "data-cls": this.cls})
    },
})

function createSuggestRow(options: {
    id: string
    index: number
    name: string
    description?: string
    isStringLabel?: boolean
}): HTMLElement {
    const row = document.createElement("div")
    row.id = options.id
    row.setAttribute("data-index", String(options.index))
    row.className = options.isStringLabel ? "monaco-list-row string-label" : "monaco-list-row"
    row.innerHTML = `
        <div class="suggest-icon" style="display: block;"></div>
        <span class="monaco-icon-name-container">${options.name}</span>
        <span class="details-label">${options.description ?? ""}</span>
    `
    return row
}

function createContext(): SuggestWidgetIconsContext {
    return {
        taskIcon: MockTaskIcon,
        loadTaskIcon: ref(vi.fn()),
        largeSuggestions: ref(false),
        codeEditor: () => undefined,
        datePicker: {
            show: vi.fn(),
            remove: vi.fn(),
        },
    }
}

describe("useSuggestWidgetIcons", () => {
    let overflowContainer: HTMLElement

    beforeEach(() => {
        overflowContainer = document.createElement("div")
        overflowContainer.id = OVERFLOW_WIDGETS_ID
        document.body.appendChild(overflowContainer)
    })

    afterEach(() => {
        overflowContainer.remove()
    })

    it("an entry whose label maps to a known plugin gets its icon", async () => {
        const ctx = createContext()
        const {observeAndResize, teardown} = useSuggestWidgetIcons(ctx)
        observeAndResize()

        const widget = document.createElement("div")
        widget.className = "suggest-widget"
        const resizer = document.createElement("div")
        resizer.className = "monaco-sash vertical disabled"
        widget.appendChild(resizer)

        const pluginRow = createSuggestRow({
            id: "row-plugin-1",
            index: 0,
            name: "Log",
            description: "io.kestra.plugin.core.log",
        })
        widget.appendChild(pluginRow)

        overflowContainer.appendChild(widget)
        await new Promise((resolve) => setTimeout(resolve, 50))

        const vsCodeIcon = pluginRow.querySelector(".suggest-icon") as HTMLElement
        expect(vsCodeIcon.style.display).toBe("none")

        const iconWrapper = pluginRow.querySelector(".kestra-icon-wrapper")
        expect(iconWrapper).not.toBeNull()
        expect(iconWrapper?.querySelector(".mock-task-icon")?.getAttribute("data-cls")).toBe(
            "io.kestra.plugin.core.log.Log",
        )

        teardown()
    })

    it("an entry with no matching plugin is left alone", async () => {
        const ctx = createContext()
        const {observeAndResize, teardown} = useSuggestWidgetIcons(ctx)
        observeAndResize()

        const widget = document.createElement("div")
        widget.className = "suggest-widget"
        const resizer = document.createElement("div")
        resizer.className = "monaco-sash vertical disabled"
        widget.appendChild(resizer)

        const variableRow = createSuggestRow({
            id: "row-var-1",
            index: 0,
            name: "myPlainVariable",
            isStringLabel: true,
        })
        widget.appendChild(variableRow)

        overflowContainer.appendChild(widget)
        await new Promise((resolve) => setTimeout(resolve, 50))

        const vsCodeIcon = variableRow.querySelector(".suggest-icon") as HTMLElement
        expect(vsCodeIcon.style.display).toBe("")

        const iconWrapper = variableRow.querySelector(".kestra-icon-wrapper")
        expect(iconWrapper).toBeNull()

        teardown()
    })

    it("the icon is not injected twice for the same entry", async () => {
        const ctx = createContext()
        const {observeAndResize, teardown} = useSuggestWidgetIcons(ctx)
        observeAndResize()

        const widget = document.createElement("div")
        widget.className = "suggest-widget"
        const resizer = document.createElement("div")
        resizer.className = "monaco-sash vertical disabled"
        widget.appendChild(resizer)

        const pluginRow = createSuggestRow({
            id: "row-plugin-dedupe",
            index: 0,
            name: "Log",
            description: "io.kestra.plugin.core.log",
        })
        widget.appendChild(pluginRow)

        overflowContainer.appendChild(widget)
        await new Promise((resolve) => setTimeout(resolve, 50))

        expect(pluginRow.querySelectorAll(".kestra-icon-wrapper")).toHaveLength(1)

        // Trigger mutation on widget
        widget.appendChild(document.createTextNode("trigger-mutation"))
        await new Promise((resolve) => setTimeout(resolve, 50))

        expect(pluginRow.querySelectorAll(".kestra-icon-wrapper")).toHaveLength(1)

        teardown()
    })

    it("the observer is disconnected on teardown", async () => {
        const ctx = createContext()
        const {observeAndResize, teardown} = useSuggestWidgetIcons(ctx)
        observeAndResize()

        const widget = document.createElement("div")
        widget.className = "suggest-widget"
        const resizer = document.createElement("div")
        resizer.className = "monaco-sash vertical disabled"
        widget.appendChild(resizer)
        overflowContainer.appendChild(widget)
        await new Promise((resolve) => setTimeout(resolve, 50))

        teardown()

        const newPluginRow = createSuggestRow({
            id: "row-plugin-after-teardown",
            index: 1,
            name: "Bash",
            description: "io.kestra.plugin.scripts.bash",
        })
        widget.appendChild(newPluginRow)
        await new Promise((resolve) => setTimeout(resolve, 50))

        const iconWrapper = newPluginRow.querySelector(".kestra-icon-wrapper")
        expect(iconWrapper).toBeNull()
    })

    it("no suggest widget present is a no-op", () => {
        overflowContainer.remove()

        const ctx = createContext()
        const {observeAndResize, teardown} = useSuggestWidgetIcons(ctx)

        expect(() => observeAndResize()).not.toThrow()
        expect(() => teardown()).not.toThrow()
    })
})
