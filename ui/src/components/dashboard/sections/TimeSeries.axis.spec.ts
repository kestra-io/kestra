import {describe, it, expect, vi} from "vitest"
import {defineComponent, h, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import {createPinia} from "pinia"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import EXECUTIONS_CHART from "../assets/executions_timeseries_chart.yaml?raw"
import type {Chart} from "../composables/useDashboards"
import TimeSeries from "./TimeSeries.vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({name: "home", params: {}, query: {}}),
}))

vi.mock("../composables/chartDrillDown", () => ({
    useChartDrillDown: () => ({drillDown: vi.fn()}),
}))

vi.mock("../composables/useDashboards", async (importOriginal) => ({
    ...await importOriginal<typeof import("../composables/useDashboards")>(),
    useChartGenerator: () => ({
        data: ref({total: 1, results: [{date: "2024-03-15T10:00:00.000Z", state: "SUCCESS", total: 1, duration: 2.5}]}),
        loading: ref(false),
        generate: vi.fn(),
    }),
}))

const KsEchartStub = defineComponent({
    name: "KsEchart",
    props: {options: {type: Object, default: () => ({})}},
    setup(_, {expose}) {
        expose({getEchartsInstance: () => null})
        return () => h("div")
    },
})

describe("TimeSeries value axes", () => {
    it("ticks the count axis in whole numbers and leaves the duration axis free", async () => {
        const wrapper = i18nMount(TimeSeries, {
            props: {chart: YAML_UTILS.parse<Chart>(EXECUTIONS_CHART)!, showDefault: true},
            global: {plugins: [createPinia()], stubs: {KsEchart: KsEchartStub, ChartLegend: true}},
        })
        await flushPromises()

        const [count, duration] = (wrapper.getComponent(KsEchartStub).props("options") as {yAxis: {minInterval?: number}[]}).yAxis
        expect(count.minInterval).toBe(1)
        expect(duration.minInterval).toBeUndefined()
    })
})
