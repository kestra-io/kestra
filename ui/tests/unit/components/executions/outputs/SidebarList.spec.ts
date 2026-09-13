import {describe, expect, test, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

vi.mock("@kestra-io/design-system", () => ({
    KsSearch: {
        props: ["modelValue", "placeholder"],
        emits: ["update:modelValue"],
        template: `
            <input
                :value="modelValue"
                :placeholder="placeholder"
                @input="$emit('update:modelValue', $event.target.value)"
            />
        `,
    },
    KsScrollbar: {template: "<div><slot /></div>"},
    KsCollapse: {template: "<div><slot /></div>"},
    KsCollapseItem: {template: "<section><slot name=\"title\" /><slot /></section>"},
    KsTag: {template: "<span><slot /></span>"},
}))

import SidebarList, {type ExplorerSection} from "../../../../../src/components/executions/outputs/SidebarList.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            variable_explorer: {
                empty: "No results",
                search_placeholder: "Search Key or value...",
            },
        },
    },
})

const sections: ExplorerSection[] = [
    {
        key: "tasksOutputs",
        label: "Tasks outputs",
        items: [
            {
                label: "http_request",
                value: {code: 200, body: "healthy"},
                type: "object",
                preview: "{ code, body }",
                expression: "outputs.http_request",
                taskRunId: "run-http",
            },
            {
                label: "check_status",
                value: {passed: true},
                type: "object",
                preview: "{ passed }",
                expression: "outputs.check_status",
                taskRunId: "run-check",
            },
        ],
    },
]

function mountSidebarList() {
    return mount(SidebarList, {
        props: {sections},
        global: {
            plugins: [i18n],
            stubs: {
                KsNoData: {props: ["title"], template: "<div>{{ title }}</div>"},
            },
        },
    })
}

describe("SidebarList search", () => {
    test("filters task runs by output value", async () => {
        const wrapper = mountSidebarList()

        await wrapper.find("input").setValue("200")

        expect(wrapper.text()).toContain("http_request")
        expect(wrapper.text()).not.toContain("check_status")
    })

    test("filters task runs case-insensitively by output key", async () => {
        const wrapper = mountSidebarList()

        await wrapper.find("input").setValue("CODE")

        expect(wrapper.text()).toContain("http_request")
        expect(wrapper.text()).not.toContain("check_status")
    })

    test("emits search changes to parent", async () => {
        const wrapper = mountSidebarList()

        await wrapper.find("input").setValue("code")

        expect(wrapper.emitted("search-change")?.at(-1)).toEqual(["code"])
    })
})
