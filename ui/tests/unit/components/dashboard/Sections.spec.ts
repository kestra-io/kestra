import {describe, it, expect, vi} from "vitest"
import type {PropType} from "vue"
import {flushPromises} from "@vue/test-utils"
import type {QueryFilterField} from "@kestra-io/kestra-sdk"
import {i18nMount} from "../../i18nMount"

import KestraDesignSystem from "@kestra-io/design-system"
import KsDropdown from "@kestra-io/design-system/components/Navigation/KsDropdown/KsDropdown.vue"
import KsButton from "@kestra-io/design-system/components/Basic/KsButton/KsButton.vue"
import KsTooltip from "@kestra-io/design-system/components/Feedback/KsTooltip.vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({name: "dashboards", params: {}, query: {}}),
}))

const {exportChart, warning, ignoredFiltersReporter} = vi.hoisted(() => ({
    exportChart: vi.fn(() => true),
    warning: vi.fn(),
    ignoredFiltersReporter: {report: undefined as ((fields: QueryFilterField[]) => void) | undefined},
}))

vi.mock("../../../../src/stores/dashboard", () => ({
    useDashboardStore: () => ({export: exportChart}),
}))

vi.mock("../../../../src/utils/toast", () => ({
    useToast: () => ({warning}),
}))

vi.mock("../../../../src/components/dashboard/dashboard-types", async () => {
    const {defineComponent, h, inject} = await import("vue")
    const {IGNORED_FILTERS_INJECTION_KEY} = await import("../../../../src/components/dashboard/composables/useDashboards")

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

import Sections from "../../../../src/components/dashboard/sections/Sections.vue"
import en from "../../../../src/translations/en.json"

function mountSections(charts: any[], stubs?: Record<string, any>) {
    return i18nMount(Sections, {
        locales: en,
        props: {
            dashboard: {id: "default", title: "", deleted: false, charts},
            charts,
        },
        global: {plugins: [KestraDesignSystem], stubs},
    })
}

describe("dashboard Sections.vue — export trigger", () => {
    it("keeps the export KsDropdown's trigger button tooltip-free, so its slot content stays a single root", () => {
        const wrapper = mountSections([{id: "c1", type: "stub-type", chartOptions: {width: 6}}])

        const tooltip = wrapper.findComponent(KsTooltip)
        expect(tooltip.exists()).toBe(true)

        const dropdown = tooltip.findComponent(KsDropdown)
        expect(dropdown.exists()).toBe(true)

        // Regression guard: a `:tooltip` prop on the trigger KsButton makes KsButton render
        // itself wrapped in its own KsTooltip, giving KsDropdown's slot content a non-single-
        // element root and breaking ElDropdown's click-trigger binding (dropdown never opens).
        // The tooltip must live on the KsTooltip wrapping KsDropdown, not on the button.
        const triggerButton = dropdown.findComponent(KsButton)
        expect(triggerButton.props("tooltip")).toBeUndefined()
        expect(triggerButton.attributes("aria-label")).toBe("Export")
    })

    it("exports the page and the quick filter the chart is currently showing", async () => {
        const chart = {id: "recent_executions", type: "stub-type", chartOptions: {width: 6}}
        const wrapper = mountSections([chart], {
            KsDropdown: {template: "<div><slot /><slot name=\"dropdown\" /></div>"},
            KsDropdownMenu: {template: "<div><slot /></div>"},
            KsDropdownItem: {emits: ["click"], template: "<button class=\"export-item\" @click=\"$emit('click')\"><slot /></button>"},
        })

        await flushPromises()

        await wrapper.findAll("button.export-item")[0].trigger("click")

        expect(exportChart).toHaveBeenCalledWith(
            expect.objectContaining({id: "default"}),
            chart,
            {
                pageNumber: 2,
                pageSize: 10,
                filters: [{field: "state", operation: "IN", value: ["FAILED"]}],
            },
            "CSV",
        )
    })

    it("warns instead of staying silent when the export produced nothing", async () => {
        exportChart.mockResolvedValueOnce(false)

        const wrapper = mountSections([{id: "recent_executions", type: "stub-type", chartOptions: {width: 6}}], {
            KsDropdown: {template: "<div><slot /><slot name=\"dropdown\" /></div>"},
            KsDropdownMenu: {template: "<div><slot /></div>"},
            KsDropdownItem: {emits: ["click"], template: "<button class=\"export-item\" @click=\"$emit('click')\"><slot /></button>"},
        })
        await flushPromises()

        await wrapper.findAll("button.export-item")[0].trigger("click")
        await flushPromises()

        expect(warning).toHaveBeenCalledWith(en.en.dashboards.exportEmpty)
    })

    it("renders no export trigger for a Markdown chart", () => {
        const wrapper = mountSections([{
            id: "notes",
            type: "io.kestra.plugin.core.dashboard.chart.Markdown",
            chartOptions: {width: 6},
        }])

        expect(wrapper.findComponent(KsDropdown).exists()).toBe(false)
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
