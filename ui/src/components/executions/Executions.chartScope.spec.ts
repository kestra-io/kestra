import {defineComponent, h} from "vue"
import {afterEach, beforeEach, describe, expect, it} from "vitest"

import {i18nShallowMount} from "../../../tests/unit/i18nMount"
import {routedPinia} from "../../../tests/unit/memoryRouter"
import Sections from "../dashboard/sections/Sections.vue"
import Executions from "./Executions.vue"

const KsDataTable = defineComponent({
    name: "KsDataTable",
    setup(_, {slots}) {
        return () => h("div", slots.top?.())
    },
})

function chartFilters(props: {namespace: string; flowId?: string}) {
    const wrapper = i18nShallowMount(Executions, {
        props: {...props, topbar: false},
        global: {plugins: [routedPinia()], stubs: {KsDataTable}},
    })

    return wrapper.findComponent(Sections).props("baseFilters")
}

describe("Executions chart scope", () => {
    let originalTitle: string

    beforeEach(() => {
        originalTitle = document.title
    })

    afterEach(() => {
        document.title = originalTitle
    })

    it("counts only the executions of its flow when shown outside the flow page", () => {
        expect(chartFilters({namespace: "qa.concurrency", flowId: "queued_chart"})).toEqual([
            {field: "namespace", operation: "EQUALS", value: "qa.concurrency"},
            {field: "flowId", operation: "EQUALS", value: "queued_chart"},
        ])
    })

    it("counts the executions of a namespace and of its children", () => {
        expect(chartFilters({namespace: "qa"})).toEqual([
            {field: "namespace", operation: "PREFIX", value: "qa"},
        ])
    })
})
