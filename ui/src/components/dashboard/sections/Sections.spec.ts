import {describe, it, expect, vi} from "vitest"
import type {PropType} from "vue"
import {flushPromises} from "@vue/test-utils"
import type {QueryFilterField} from "@kestra-io/kestra-sdk"
import {i18nMount} from "../../../../tests/unit/i18nMount"

import KestraDesignSystem from "@kestra-io/design-system"

vi.mock("vue-router", () => ({
    useRoute: () => ({name: "dashboards", params: {}, query: {}}),
}))

const {exportChart, warning, ignoredFiltersReporter} = vi.hoisted(() => ({
    exportChart: vi.fn(),
    warning: vi.fn(),
    ignoredFiltersReporter: {report: undefined as ((fields: QueryFilterField[]) => void) | undefined},
}))

vi.mock("../../../stores/dashboard", () => ({
    useDashboardStore: () => ({export: exportChart}),
}))

vi.mock("../../../utils/toast", () => ({
    useToast: () => ({warning}),
}))

vi.mock("../dashboard-types", async () => {
    const {defineComponent, h, inject} = await import("vue")
    const {IGNORED_FILTERS_INJECTION_KEY} = await import("../composables/useDashboards")

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
            "reporting-type": defineComponent({
                props: {chart: {type: Object as PropType<{id: string}>, required: true}},
                setup(props) {
                    const report = inject(IGNORED_FILTERS_INJECTION_KEY)
                    ignoredFiltersReporter.report = (fields) => report?.(props.chart.id, fields)

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
