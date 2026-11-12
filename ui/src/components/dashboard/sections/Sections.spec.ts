import {describe, it, expect, vi} from "vitest"
import type {PropType} from "vue"
import {flushPromises} from "@vue/test-utils"
import type {QueryFilterField} from "@kestra-io/kestra-sdk"
import {i18nMount} from "../../../../tests/unit/i18nMount"

import KestraDesignSystem from "@kestra-io/design-system"

vi.mock("vue-router", () => ({
    useRoute: () => ({name: "dashboards", params: {}, query: {}}),
}))

const {exportChart, warning, ignoredFiltersReporter, noticeReporter} = vi.hoisted(() => ({
    exportChart: vi.fn(),
    warning: vi.fn(),
    ignoredFiltersReporter: {report: undefined as ((fields: QueryFilterField[]) => void) | undefined},
    noticeReporter: {report: undefined as ((notice: {metadata?: {executionId: string; date: string; state: string; rowsRead: number; truncated: boolean}; warning?: string}) => void) | undefined},
}))

vi.mock("../../../stores/dashboard", () => ({
    useDashboardStore: () => ({export: exportChart}),
}))

vi.mock("../../../utils/toast", () => ({
    useToast: () => ({warning}),
}))

vi.mock("../dashboard-types", async () => {
    const {defineComponent, h, inject} = await import("vue")
    const {IGNORED_FILTERS_INJECTION_KEY, CHART_NOTICE_INJECTION_KEY} = await import("../composables/useDashboards")

    return {
        TYPES: {
            "stub-type": {
                template: "<div />",
                methods: {
                    refresh() {},
                    exportParameters() {
                        return {
                            pageNumber: 2,
                            pageSize: 10,
                            filters: [{field: "state", operation: "IN", value: ["FAILED"]}],
                        }
                    },
                },
            },
            "tabbed-type": {
                template: "<div />",
                methods: {
                    refresh() {},
                    exportParameters() {
                        return {filters: []}
                    },
                    exportChart() {
                        return {id: "recent_executions__upcoming", type: "tabbed-type"}
                    },
                },
            },
            "reporting-type": defineComponent({
                props: {chart: {type: Object as PropType<{id: string}>, required: true}},
                setup(props) {
                    const report = inject(IGNORED_FILTERS_INJECTION_KEY)
                    ignoredFiltersReporter.report = (fields) => report?.(props.chart.id, fields)
                    const reportNotice = inject(CHART_NOTICE_INJECTION_KEY)
                    noticeReporter.report = (notice) => reportNotice?.(props.chart.id, notice)

                    return () => h("div")
                },
            }),
        },
    }
})

import Sections from "./Sections.vue"
import en from "../../../translations/en.json"
import type {Chart} from "../types"

function mountSections(charts: Chart[]) {
    return i18nMount(Sections, {
        locales: en,
        props: {
            dashboard: {id: "default", title: "", deleted: false, charts},
            charts,
        },
        global: {plugins: [KestraDesignSystem]},
    })
}

const EXPORT_BUTTON = "button[aria-label='Export to CSV']"

describe("dashboard Sections.vue — export trigger", () => {
    it("exports the page and the quick filter the chart is currently showing", async () => {
        const chart = {id: "recent_executions", type: "stub-type", chartOptions: {width: 6}}
        const wrapper = mountSections([chart])

        await flushPromises()

        await wrapper.get(EXPORT_BUTTON).trigger("click")

        expect(exportChart).toHaveBeenCalledWith(
            expect.objectContaining({id: "default"}),
            chart,
            {
                pageNumber: 2,
                pageSize: 10,
                filters: [{field: "state", operation: "IN", value: ["FAILED"]}],
            },
        )
    })

    it("exports the chart the component is showing rather than the declared one", async () => {
        const chart = {id: "recent_executions", type: "tabbed-type", chartOptions: {width: 12}}
        const wrapper = mountSections([chart])

        await flushPromises()

        await wrapper.get(EXPORT_BUTTON).trigger("click")

        expect(exportChart).toHaveBeenCalledWith(
            expect.objectContaining({id: "default"}),
            expect.objectContaining({id: "recent_executions__upcoming"}),
            expect.anything(),
        )
    })

    it("renders no export trigger for a Markdown chart", () => {
        const wrapper = mountSections([{
            id: "notes",
            type: "io.kestra.plugin.core.dashboard.chart.Markdown",
            chartOptions: {width: 6},
        }])

        expect(wrapper.find(EXPORT_BUTTON).exists()).toBe(false)
    })
})

describe("dashboard Sections.vue ignored filters", () => {
    it("warns once per chart until the filters it ignores change", async () => {
        mountSections([{id: "logs", type: "reporting-type", chartOptions: {width: 6, displayName: "Logs by level"}}])
        await flushPromises()
        warning.mockClear()

        ignoredFiltersReporter.report?.(["state"])
        ignoredFiltersReporter.report?.(["state"])
        ignoredFiltersReporter.report?.([])
        ignoredFiltersReporter.report?.(["state"])

        expect(warning).toHaveBeenCalledTimes(2)
        expect(warning).toHaveBeenLastCalledWith(expect.stringContaining("doesn't apply to this chart"), "Logs by level")
    })
})

describe("dashboard Sections.vue chart notice", () => {
    const chart = {id: "rejected", type: "reporting-type", chartOptions: {width: 6, displayName: "Rejected orders"}}

    it("shows under the title which run the rows come from", async () => {
        const wrapper = mountSections([chart])
        await flushPromises()

        noticeReporter.report?.({metadata: {executionId: "e1", date: "2026-11-09T10:00:00Z", state: "SUCCESS", rowsRead: 4, truncated: false}})
        await flushPromises()

        expect(wrapper.get(".chart-metadata").text()).toContain("Updated")
        expect(wrapper.find("[data-test=chart-title-warning]").exists()).toBe(false)
        expect(wrapper.find(".chart-warning").exists()).toBe(false)
    })

    it("warns next to the date that the rows were cut at the data source limit", async () => {
        const wrapper = mountSections([chart])
        await flushPromises()

        noticeReporter.report?.({metadata: {executionId: "e1", date: "2026-11-09T10:00:00Z", state: "SUCCESS", rowsRead: 10000, truncated: true}})
        await flushPromises()

        expect(wrapper.get("[data-test=chart-title-warning]").attributes("aria-label")).toBe("Output file has been truncated to its 10000 first rows. See documentation for further details.")
    })

    it("shows the warning of a chart that has no data to read, and removes it when it clears", async () => {
        const wrapper = mountSections([chart])
        await flushPromises()

        noticeReporter.report?.({warning: "The file of the output 'rejected' was deleted by the retention."})
        await flushPromises()
        expect(wrapper.get(".chart-warning").text()).toContain("deleted by the retention")
        expect(wrapper.get("[data-test=chart-title-warning]").attributes("aria-label")).toContain("deleted by the retention")
        expect(wrapper.get(".chart-body").classes()).toContain("is-hidden")
        expect(wrapper.find(".chart-metadata").exists()).toBe(false)

        noticeReporter.report?.({})
        await flushPromises()
        expect(wrapper.find(".chart-warning").exists()).toBe(false)
        expect(wrapper.get(".chart-body").classes()).not.toContain("is-hidden")
    })
})
