import {beforeEach, describe, it, expect, vi} from "vitest"
import TaskEditData from "../../../../src/components/flows/TaskEditData.vue"
import type {DataSection} from "../../../../src/components/flows/contextSections/types"
import {i18nMount} from "../../i18nMount"

const posthogEvents = vi.fn()
vi.mock("../../../../src/stores/api", () => ({
    useApiStore: () => ({posthogEvents}),
}))

const messages = {
    expand: "Expand",
    collapse: "Collapse",
    new: "New",
    block_editor: {filter_data: "Filter", no_data_matches: "No data matches", chip_hint: "Click a chip to insert it, or drag it in"},
}

const sections = [
    {key: "up", label: "Upstream outputs", chips: [
        {label: "flow.id", expr: "{{ flow.id }}"},
        {label: "execution.state", expr: "{{ execution.state }}"},
    ]},
    {key: "ctx", label: "Execution context", chips: [
        {label: "taskrun.id", expr: "{{ taskrun.id }}"},
    ]},
]

function render(overrideProps: Partial<{sections: DataSection[], defaultCollapsedKeys: string[]}> = {}) {
    return i18nMount(TaskEditData, {
        messages,
        props: {kind: "inputs", title: "Inputs", subtitle: "data you can use", sections, filterable: true, ...overrideProps},
    })
}

describe("TaskEditData filtering", () => {
    it("shows every chip when the filter is empty", () => {
        const text = render().text()
        expect(text).toContain("flow.id")
        expect(text).toContain("execution.state")
        expect(text).toContain("taskrun.id")
    })

    it("keeps only chips matching the query and hides empty sections", async () => {
        const wrapper = render()
        await wrapper.get("input").setValue("flow")

        expect(wrapper.text()).toContain("flow.id")
        expect(wrapper.text()).not.toContain("execution.state")
        expect(wrapper.text()).not.toContain("taskrun.id")
        expect(wrapper.text()).not.toContain("Execution context")
    })

    it("matches across sections by label substring", async () => {
        const wrapper = render()
        await wrapper.get("input").setValue("id")

        expect(wrapper.text()).toContain("flow.id")
        expect(wrapper.text()).toContain("taskrun.id")
        expect(wrapper.text()).not.toContain("execution.state")
    })

    it("shows the empty state when nothing matches", async () => {
        const wrapper = render()
        await wrapper.get("input").setValue("zzz")

        expect(wrapper.text()).toContain("No data matches")
        expect(wrapper.text()).not.toContain("flow.id")
    })
})

describe("TaskEditData chip activation", () => {
    it("shows a permanent hint that clicking inserts and dragging still works", () => {
        expect(render().text()).toContain("Click a chip to insert it, or drag it in")
    })

    it("emits chip-activate with the expression and section key when an interactive chip is clicked", async () => {
        const wrapper = render()
        await wrapper.get("[title='{{ flow.id }}']").trigger("click")

        expect(wrapper.emitted("chip-activate")).toEqual([["{{ flow.id }}", "up"]])
    })

    it("does not render a hint or interactive chips for a non-interactive column", () => {
        const wrapper = i18nMount(TaskEditData, {
            messages,
            props: {kind: "output", title: "Output", subtitle: "what this task produces", sections, interactive: false},
        })

        expect(wrapper.text()).not.toContain("Click a chip to insert it, or drag it in")
        expect(wrapper.find("button.task-edit-data-chip").exists()).toBe(false)
    })
})

describe("TaskEditData collapsing", () => {
    it("starts every section expanded when no defaultCollapsedKeys is given", () => {
        expect(render().text()).toContain("taskrun.id")
    })

    it("starts a section collapsed when its key is in defaultCollapsedKeys", () => {
        const wrapper = render({defaultCollapsedKeys: ["ctx"]})

        expect(wrapper.text()).toContain("flow.id")
        expect(wrapper.text()).not.toContain("taskrun.id")
    })

    it("force-expands a collapsed section once the filter matches one of its chips", async () => {
        const wrapper = render({defaultCollapsedKeys: ["ctx"]})
        expect(wrapper.text()).not.toContain("taskrun.id")

        await wrapper.get("input").setValue("taskrun")

        expect(wrapper.text()).toContain("taskrun.id")
    })

    it("re-collapses once the matching filter is cleared", async () => {
        const wrapper = render({defaultCollapsedKeys: ["ctx"]})
        await wrapper.get("input").setValue("taskrun")
        expect(wrapper.text()).toContain("taskrun.id")

        await wrapper.get("input").setValue("")

        expect(wrapper.text()).not.toContain("taskrun.id")
    })
})

describe("TaskEditData new-section badge", () => {
    it("shows a New badge only for a section flagged isNew", () => {
        const wrapper = render({sections: [{...sections[0], isNew: true}, sections[1]]})
        const text = wrapper.text()

        expect(text).toContain("New")
    })

    it("shows no New badge when no section is flagged", () => {
        expect(render().text()).not.toContain("New")
    })
})

describe("TaskEditData analytics", () => {
    beforeEach(() => posthogEvents.mockClear())

    it("tracks a section expansion, scoped to the panel kind", async () => {
        const wrapper = render({defaultCollapsedKeys: ["ctx"]})
        const headers = wrapper.findAll(".task-edit-data-section-head")
        await headers[1].trigger("click")

        expect(posthogEvents).toHaveBeenCalledWith({type: "CONTEXT_SECTION_EXPANDED", section: "inputs.ctx"})
    })

    it("does not track a collapse", async () => {
        const wrapper = render()
        const headers = wrapper.findAll(".task-edit-data-section-head")
        await headers[0].trigger("click")

        expect(posthogEvents).not.toHaveBeenCalledWith(expect.objectContaining({type: "CONTEXT_SECTION_EXPANDED"}))
    })

    it("does not track anything on click — the outcome (insert vs copy) is decided by the parent", async () => {
        const wrapper = render()
        await wrapper.get(".task-edit-data-chip").trigger("click")

        expect(posthogEvents).not.toHaveBeenCalled()
    })

    it("does not track a chip insertion just from starting a drag — only a successful drop, tracked by the parent, counts", async () => {
        const wrapper = render()
        await wrapper.get(".task-edit-data-chip").trigger("dragstart", {dataTransfer: {setData: vi.fn(), effectAllowed: ""}})

        expect(posthogEvents).not.toHaveBeenCalledWith(expect.objectContaining({type: "CHIP_INSERTED"}))
    })

    it("carries the section key alongside the chip expression when a drag starts", async () => {
        const setData = vi.fn()
        const wrapper = render()
        await wrapper.get(".task-edit-data-chip").trigger("dragstart", {dataTransfer: {setData, effectAllowed: ""}})

        expect(setData).toHaveBeenCalledWith("application/x-kestra-chip-section", "up")
    })
})

describe("TaskEditData toggle", () => {
    it("does not collapse or track a section while a filter is active", async () => {
        const wrapper = render()
        await wrapper.get("input").setValue("flow")

        const header = wrapper.get(".task-edit-data-section-head")
        await header.trigger("click")

        expect(wrapper.text()).toContain("flow.id")
        posthogEvents.mockClear()
        await header.trigger("click")
        expect(posthogEvents).not.toHaveBeenCalled()
    })
})

describe("TaskEditData section count", () => {
    it("counts distinct entries, not chips, when several chips share the same groupKey", () => {
        const wrapper = render({sections: [
            {key: "files", label: "Namespace files", chips: [
                {label: "read('a.sql')", expr: "{{ read('a.sql') }}", groupKey: "a.sql"},
                {label: "fileURI('a.sql')", expr: "{{ fileURI('a.sql') }}", groupKey: "a.sql"},
                {label: "fileURI('b.json')", expr: "{{ fileURI('b.json') }}", groupKey: "b.json"},
            ]},
        ]})

        const count = wrapper.get(".task-edit-data-count")
        expect(count.text()).toBe("2")
    })
})
