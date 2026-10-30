import {describe, expect, it} from "vitest"
import TableQuickFilter from "./TableQuickFilter.vue"
import {QUICK_FILTER_TABS} from "./quickFilters"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const EXECUTIONS = "io.kestra.plugin.core.dashboard.data.Executions"

const ALL_STATES = [
    "SUBMITTED", "CREATED", "RESTARTED", "QUEUED", "RUNNING", "PAUSING", "RETRYING", "KILLING",
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
    i18nMount(TableQuickFilter, {
        props: {chart: chart as never},
        global: {stubs: {Motion: true}},
    })

const tabButtons = (wrapper: ReturnType<typeof mountFilter>) =>
    wrapper.findAll("button.quick-filter-tab")

const lastChange = (wrapper: ReturnType<typeof mountFilter>) =>
    (wrapper.emitted("change") as [unknown, string][]).at(-1)

describe("TableQuickFilter", () => {
    it("renders nothing for a chart with no quick filters", () => {
        const wrapper = mountFilter({
            id: "logs",
            type: "io.kestra.plugin.core.dashboard.chart.Table",
            data: {type: "io.kestra.plugin.core.dashboard.data.Logs"},
        })

        expect(tabButtons(wrapper)).toHaveLength(0)
    })

    it("renders one tab per entry of QUICK_FILTER_TABS when it has quick filters", () => {
        const wrapper = mountFilter(executionsChart())

        expect(tabButtons(wrapper)).toHaveLength(QUICK_FILTER_TABS.length)
    })

    it("has the first tab active by default", () => {
        const wrapper = mountFilter(executionsChart())
        const buttons = tabButtons(wrapper)

        expect(buttons[0].classes()).toContain("active")
        expect(buttons[0].find(".tab-indicator").exists()).toBe(true)
        expect(buttons.slice(1).every((btn) => !btn.classes().includes("active"))).toBe(true)
    })

    it("emits nothing when clicking the active tab", async () => {
        const wrapper = mountFilter(executionsChart())
        const buttons = tabButtons(wrapper)

        await buttons[0].trigger("click")

        expect(wrapper.emitted("change")).toBeUndefined()

        await buttons[1].trigger("click")
        expect(wrapper.emitted("change")).toHaveLength(1)

        await buttons[1].trigger("click")
        expect(wrapper.emitted("change")).toHaveLength(1)
    })

    it("moves the active class and the indicator to the selected tab", async () => {
        const wrapper = mountFilter(executionsChart())
        const buttons = tabButtons(wrapper)

        await buttons[1].trigger("click")

        expect(buttons[0].classes()).not.toContain("active")
        expect(buttons[0].find(".tab-indicator").exists()).toBe(false)
        expect(buttons[1].classes()).toContain("active")
        expect(buttons[1].find(".tab-indicator").exists()).toBe(true)
    })

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
