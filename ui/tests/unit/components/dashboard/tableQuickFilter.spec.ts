import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import TableQuickFilter from "../../../../src/components/dashboard/sections/TableQuickFilter.vue"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false})

const EXECUTIONS = "io.kestra.plugin.core.dashboard.data.Executions"

const ALL_STATES = [
    "SUBMITTED", "CREATED", "RESTARTED", "QUEUED", "RUNNING", "RETRYING", "KILLING",
    "PAUSED", "BREAKPOINT",
    "SUCCESS",
    "WARNING",
    "FAILED", "KILLED", "CANCELLED", "SKIPPED", "RETRIED",
]

const executionsChart = (where?: unknown) => ({
    id: "executions",
    type: "io.kestra.plugin.core.dashboard.chart.Table",
    data: {type: EXECUTIONS, columns: {state: {field: "STATE"}}, ...(where ? {where} : {})},
})

const mountFilter = (chart: Record<string, unknown>) =>
    mount(TableQuickFilter, {
        props: {chart: chart as never},
        global: {plugins: [i18n], stubs: {Motion: true}},
    })

const tabButtons = (wrapper: ReturnType<typeof mountFilter>) =>
    wrapper.findAll("button.quick-filter-tab")

const lastChange = (wrapper: ReturnType<typeof mountFilter>) =>
    (wrapper.emitted("change") as [unknown, string][]).at(-1)

describe("TableQuickFilter", () => {
    it("emits no state filter for the All tab when the chart does not constrain STATE", async () => {
        const wrapper = mountFilter(executionsChart())
        const buttons = tabButtons(wrapper)

        await buttons[1].trigger("click")
        await buttons[0].trigger("click")

        expect(lastChange(wrapper)).toEqual([null, "all"])
    })

    it("emits every state for the All tab when the chart constrains STATE via where", async () => {
        const wrapper = mountFilter(
            executionsChart([{type: "IN", field: "STATE", values: ["RUNNING", "PAUSED"]}]),
        )
        const buttons = tabButtons(wrapper)

        await buttons[1].trigger("click")
        await buttons[0].trigger("click")

        expect(lastChange(wrapper)).toEqual([
            {field: "state", operation: "IN", value: ALL_STATES},
            "all",
        ])
    })

    it("emits the tab's own states for a state tab", async () => {
        const wrapper = mountFilter(executionsChart())

        await tabButtons(wrapper)[5].trigger("click")

        expect(lastChange(wrapper)).toEqual([
            {field: "state", operation: "IN", value: ["FAILED", "KILLED", "CANCELLED", "SKIPPED", "RETRIED"]},
            "failed",
        ])
    })
})
