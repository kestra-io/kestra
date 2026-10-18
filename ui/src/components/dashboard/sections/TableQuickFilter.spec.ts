import {describe, expect, it} from "vitest"
import TableQuickFilter from "./TableQuickFilter.vue"
import {QUICK_FILTER_TABS, stateFilterForTab} from "./quickFilters"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import type {Chart} from "../types"

const EXECUTIONS_DATA_TYPE = "io.kestra.plugin.core.dashboard.data.Executions"

const chartWithQuickFilters = (): Chart => ({
    id: "executions-chart",
    type: "io.kestra.plugin.core.dashboard.chart.Table",
    data: {
        type: EXECUTIONS_DATA_TYPE,
        columns: {
            state: {field: "STATE"},
        },
    },
})

const chartWithoutQuickFilters = (): Chart => ({
    id: "logs-chart",
    type: "io.kestra.plugin.core.dashboard.chart.Table",
    data: {
        type: "io.kestra.plugin.core.dashboard.data.Logs",
        columns: {
            message: {field: "MESSAGE"},
        },
    },
})

const mountTableQuickFilter = (chart: Chart) =>
    i18nMount(TableQuickFilter, {
        props: {chart},
        global: {
            stubs: {
                Motion: true,
            },
        },
    })

describe("TableQuickFilter", () => {
    it("renders nothing for a chart with no quick filters", () => {
        const wrapper = mountTableQuickFilter(chartWithoutQuickFilters())
        const tabs = wrapper.findAll("button.quick-filter-tab")
        expect(tabs).toHaveLength(0)
    })

    it("renders one tab per entry of QUICK_FILTER_TABS when chart supports quick filters", () => {
        const wrapper = mountTableQuickFilter(chartWithQuickFilters())
        const tabs = wrapper.findAll("button.quick-filter-tab")
        expect(tabs).toHaveLength(QUICK_FILTER_TABS.length)

        QUICK_FILTER_TABS.forEach((tab, index) => {
            expect(tabs[index].text()).toContain(`dashboards.quick_filters.${tab.key}`)
        })
    })

    it("has the first tab active by default", () => {
        const wrapper = mountTableQuickFilter(chartWithQuickFilters())
        const tabs = wrapper.findAll("button.quick-filter-tab")

        expect(tabs[0].classes()).toContain("active")
        for (let i = 1; i < tabs.length; i++) {
            expect(tabs[i].classes()).not.toContain("active")
        }
    })

    it("emits change with that tab's state filter and key when clicking another tab", async () => {
        const chart = chartWithQuickFilters()
        const wrapper = mountTableQuickFilter(chart)
        const tabs = wrapper.findAll("button.quick-filter-tab")

        await tabs[1].trigger("click")

        const emitted = wrapper.emitted("change")
        expect(emitted).toBeDefined()
        expect(emitted).toHaveLength(1)
        expect(emitted?.[0]).toEqual([
            stateFilterForTab(chart, QUICK_FILTER_TABS[1].key),
            QUICK_FILTER_TABS[1].key,
        ])

        await tabs[3].trigger("click")
        expect(emitted).toHaveLength(2)
        expect(emitted?.[1]).toEqual([
            stateFilterForTab(chart, QUICK_FILTER_TABS[3].key),
            QUICK_FILTER_TABS[3].key,
        ])
    })

    it("emits nothing when clicking the active tab", async () => {
        const wrapper = mountTableQuickFilter(chartWithQuickFilters())
        const tabs = wrapper.findAll("button.quick-filter-tab")

        // First tab is already active
        await tabs[0].trigger("click")
        expect(wrapper.emitted("change")).toBeUndefined()

        // Switch to tab 1
        await tabs[1].trigger("click")
        expect(wrapper.emitted("change")).toHaveLength(1)

        // Click tab 1 again
        await tabs[1].trigger("click")
        expect(wrapper.emitted("change")).toHaveLength(1)
    })

    it("ensures the active tab carries the active class and the indicator", async () => {
        const wrapper = mountTableQuickFilter(chartWithQuickFilters())
        const tabs = wrapper.findAll("button.quick-filter-tab")

        expect(tabs[0].classes()).toContain("active")
        expect(tabs[0].find(".tab-indicator").exists()).toBe(true)
        expect(tabs[1].classes()).not.toContain("active")
        expect(tabs[1].find(".tab-indicator").exists()).toBe(false)

        await tabs[1].trigger("click")

        expect(tabs[0].classes()).not.toContain("active")
        expect(tabs[0].find(".tab-indicator").exists()).toBe(false)
        expect(tabs[1].classes()).toContain("active")
        expect(tabs[1].find(".tab-indicator").exists()).toBe(true)
    })
})
