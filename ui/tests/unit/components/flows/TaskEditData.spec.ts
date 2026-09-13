import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import TaskEditData from "../../../../src/components/flows/TaskEditData.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    missingWarn: false,
    fallbackWarn: false,
    messages: {en: {
        expand: "Expand",
        collapse: "Collapse",
        copied: "Copied",
        block_editor: {filter_data: "Filter", no_data_matches: "No data matches"},
    }},
})

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
    return mount(TaskEditData, {
        props: {kind: "inputs", title: "Inputs", subtitle: "data you can use", sections, filterable: true},
        global: {plugins: [i18n]},
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
