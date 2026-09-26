import {describe, it, expect} from "vitest"
import TaskEditData from "../../../../src/components/flows/TaskEditData.vue"
import {i18nMount} from "../../i18nMount"

const messages = {
    expand: "Expand",
    collapse: "Collapse",
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

function render() {
    return i18nMount(TaskEditData, {
        messages,
        props: {kind: "inputs", title: "Inputs", subtitle: "data you can use", sections, filterable: true},
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

    it("emits chip-activate with the expression when an interactive chip is clicked", async () => {
        const wrapper = render()
        await wrapper.get("[title='{{ flow.id }}']").trigger("click")

        expect(wrapper.emitted("chip-activate")).toEqual([["{{ flow.id }}"]])
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
