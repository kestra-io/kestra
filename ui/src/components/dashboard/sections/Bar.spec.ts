import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {i18nShallowMount} from "../../../../tests/unit/i18nMount"
import {CHART_RESULTS_KEY, type Chart} from "../composables/useDashboards"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, fullPath: "/"}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))

vi.mock("../composables/chartDrillDown", () => ({
    useChartDrillDown: () => ({drillDown: vi.fn()}),
}))

import Bar from "./Bar.vue"

const KsBarStub = defineComponent({
    name: "KsBar",
    props: {
        options: {
            type: Object,
            default: () => ({}),
        },
    },
    render: () => h("div"),
})

function mountBar(chart: Chart, results: Record<string, unknown>[]) {
    return i18nShallowMount(Bar, {
        props: {showDefault: true, chart},
        global: {
            provide: {
                [CHART_RESULTS_KEY as symbol]: () => ({results}),
            },
            stubs: {
                KsBar: KsBarStub,
                KsSkeleton: {template: "<div />", inheritAttrs: false},
                KsNoData: {template: "<div />", inheritAttrs: false},
                ChartLegend: {template: "<div />", inheritAttrs: false},
            },
        },
    })
}

describe("dashboard Bar axis", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        localStorage.clear()
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("sets minInterval to 1 on the value axis for COUNT aggregations", async () => {
        const chart: Chart = {
            id: "executions_per_flow",
            type: "io.kestra.plugin.core.dashboard.chart.Bar",
            content: "charts:\n  - id: executions_per_flow",
            chartOptions: {column: "flow"},
            data: {
                type: "io.kestra.plugin.core.dashboard.data.Executions",
                columns: {
                    flow: {field: "FLOW_ID"},
                    state: {field: "STATE"},
                    total: {agg: "COUNT"},
                },
            },
        }
        const wrapper = mountBar(chart, [
            {flow: "flow_one", state: "SUCCESS", total: 2},
            {flow: "flow_two", state: "SUCCESS", total: 1},
        ])
        await flushPromises()

        const options = wrapper.getComponent(KsBarStub).props("options") as {xAxis: {minInterval?: number}}
        expect(options.xAxis.minInterval).toBe(1)
    })

    it.each(["AVG", "SUM", "MIN", "MAX"])("leaves minInterval unset for the %s aggregation", async (agg) => {
        const chart: Chart = {
            id: "duration_per_flow",
            type: "io.kestra.plugin.core.dashboard.chart.Bar",
            content: "charts:\n  - id: duration_per_flow",
            chartOptions: {column: "flow"},
            data: {
                type: "io.kestra.plugin.core.dashboard.data.Executions",
                columns: {
                    flow: {field: "FLOW_ID"},
                    duration: {agg, field: "DURATION"},
                },
            },
        }
        const wrapper = mountBar(chart, [
            {flow: "flow_one", duration: 0.4},
            {flow: "flow_two", duration: 1.2},
        ])
        await flushPromises()

        const options = wrapper.getComponent(KsBarStub).props("options") as {xAxis: {minInterval?: number}}
        expect(options.xAxis.minInterval).toBeUndefined()
    })
})
