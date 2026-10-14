import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import {OVERFLOW_WIDGETS_ID} from "../utils/monacoSetup"
import {useSuggestWidgetIcons} from "./useSuggestWidgetIcons"

const PLUGIN = "io.kestra.plugin.core.log.Log"

const TaskIconStub = defineComponent({
    props: {cls: {type: String, default: undefined}},
    setup(props) {
        return () => h("span", {class: "task-icon-stub", "data-cls": props.cls})
    },
})

/** A Monaco suggest row, with the icon slot Monaco renders before the label. */
function suggestRow(id: string, label: string): HTMLElement {
    const row = document.createElement("div")
    row.className = "monaco-list-row"
    row.id = id
    row.setAttribute("data-index", "0")
    row.innerHTML = `
        <span class="suggest-icon"></span>
        <span class="monaco-icon-name-container">${label}</span>
    `
    return row
}

function suggestWidget(...rows: HTMLElement[]): HTMLElement {
    const widget = document.createElement("div")
    widget.className = "suggest-widget"
    widget.innerHTML = "<div class=\"monaco-sash vertical\"></div>"
    widget.append(...rows)
    return widget
}

function setup() {
    const overflow = document.createElement("div")
    overflow.id = OVERFLOW_WIDGETS_ID
    document.body.append(overflow)

    const icons = useSuggestWidgetIcons({
        taskIcon: TaskIconStub,
        loadTaskIcon: ref(vi.fn()),
        largeSuggestions: ref(false),
        codeEditor: () => undefined,
        datePicker: {show: vi.fn(), remove: vi.fn()},
    })
    icons.observeAndResize()

    return {overflow, ...icons}
}

async function openWidget(overflow: HTMLElement, ...rows: HTMLElement[]) {
    const widget = suggestWidget(...rows)
    overflow.append(widget)
    await flushPromises()
    return widget
}

describe("useSuggestWidgetIcons", () => {
    let teardown: (() => void) | undefined

    beforeEach(() => {
        document.body.innerHTML = ""
    })

    afterEach(() => {
        teardown?.()
        teardown = undefined
    })

    it("gives a row whose label is a plugin type its plugin icon", async () => {
        const icons = setup()
        teardown = icons.teardown
        const row = suggestRow("row-0", PLUGIN)

        await openWidget(icons.overflow, row)

        const wrapper = row.querySelector(".kestra-icon-wrapper")
        expect(wrapper?.querySelector(".task-icon-stub")?.getAttribute("data-cls")).toBe(PLUGIN)
        expect(row.querySelector<HTMLElement>(".suggest-icon")!.style.display).toBe("none")
    })

    it("leaves a row with no matching plugin alone", async () => {
        const icons = setup()
        teardown = icons.teardown
        const row = suggestRow("row-0", "namespace")

        await openWidget(icons.overflow, row)

        expect(row.querySelector(".kestra-icon-wrapper")).toBeNull()
        expect(row.querySelector<HTMLElement>(".suggest-icon")!.style.display).toBe("")
    })

    it("does not inject the icon twice for the same row", async () => {
        const icons = setup()
        teardown = icons.teardown
        const row = suggestRow("row-0", PLUGIN)
        await openWidget(icons.overflow, row)
        expect(row.querySelectorAll(".kestra-icon-wrapper")).toHaveLength(1)

        // Monaco re-renders the label text in place, which hands the same row back to the observer.
        row.querySelector(".monaco-icon-name-container")!.append(document.createTextNode(""))
        await flushPromises()

        expect(row.querySelectorAll(".kestra-icon-wrapper")).toHaveLength(1)
    })

    it("stops decorating rows once torn down", async () => {
        const icons = setup()
        const widget = await openWidget(icons.overflow, suggestRow("row-0", "namespace"))

        icons.teardown()
        const lateRow = suggestRow("row-1", PLUGIN)
        widget.append(lateRow)
        await flushPromises()

        expect(lateRow.querySelector(".kestra-icon-wrapper")).toBeNull()
    })

    it("does nothing when no suggest widget shows up", async () => {
        const icons = setup()
        teardown = icons.teardown
        const other = document.createElement("div")
        other.className = "parameter-hints-widget"
        other.append(suggestRow("row-0", PLUGIN))

        icons.overflow.append(other)
        await flushPromises()

        expect(other.querySelector(".kestra-icon-wrapper")).toBeNull()
    })

    it("does nothing when the overflow container is missing", () => {
        const icons = useSuggestWidgetIcons({
            taskIcon: TaskIconStub,
            loadTaskIcon: ref(vi.fn()),
            largeSuggestions: ref(false),
            codeEditor: () => undefined,
            datePicker: {show: vi.fn(), remove: vi.fn()},
        })

        expect(() => {
            icons.observeAndResize()
            icons.teardown()
        }).not.toThrow()
    })
})
