import {describe, expect, it} from "vitest"
import TableQuickFilter from "./TableQuickFilter.vue"
import {QUICK_FILTER_TABS} from "./quickFilters"
import {i18nMount} from "../../../../tests/unit/i18nMount"

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

const mountFilter = (chart: Record<string, unknown>, counts?: Record<string, number>) =>
    i18nMount(TableQuickFilter, {
        props: {chart: chart as never, counts: counts as never},
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
    describe("counts", () => {
        const counts = {all: 12, running: 0, paused: 3, success: 9, warning: 0, failed: 0}

        it("shows a badge with the count on every tab that has executions", () => {
            const buttons = tabButtons(mountFilter(executionsChart(), counts))

            expect(buttons[0].find(".tab-count").text()).toBe("12")
            expect(buttons[2].find(".tab-count").text()).toBe("3")
            expect(buttons[3].find(".tab-count").text()).toBe("9")
        })

        it("disables, and shows no badge on, the tabs without executions", () => {
            const buttons = tabButtons(mountFilter(executionsChart(), counts))

            for (const index of [1, 4, 5]) {
                expect(buttons[index].attributes("disabled")).toBeDefined()
                expect(buttons[index].find(".tab-count").exists()).toBe(false)
            }
            for (const index of [0, 2, 3]) {
                expect(buttons[index].attributes("disabled")).toBeUndefined()
            }
        })

        it("does not emit when clicking a disabled tab", async () => {
            const wrapper = mountFilter(executionsChart(), counts)

            await tabButtons(wrapper)[1].trigger("click")

            expect(wrapper.emitted("change")).toBeUndefined()
        })

        it("keeps every tab enabled and badge-less while the counts are loading", () => {
            const buttons = tabButtons(mountFilter(executionsChart()))

            expect(buttons.every((btn) => btn.attributes("disabled") === undefined)).toBe(true)
            expect(buttons.some((btn) => btn.find(".tab-count").exists())).toBe(false)
        })

        it("keeps the active tab enabled when a refresh empties it", async () => {
            const wrapper = mountFilter(executionsChart(), counts)
            await tabButtons(wrapper)[2].trigger("click")

            await wrapper.setProps({counts: {...counts, paused: 0} as never})

            expect(tabButtons(wrapper)[2].attributes("disabled")).toBeUndefined()
        })
    })
})
