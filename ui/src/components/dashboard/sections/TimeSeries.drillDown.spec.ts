import {describe, it, expect, vi, beforeEach} from "vitest"
import {defineComponent, h, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import {createPinia} from "pinia"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import LOGS_CHART from "../assets/logs_timeseries_chart.yaml?raw"
import type {Chart} from "../composables/useDashboards"
import TimeSeries from "./TimeSeries.vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({name: "logs/list", params: {}, query: {"filters[timeRange][EQUALS]": "PT1H"}}),
}))

const {drillDown} = vi.hoisted(() => ({drillDown: vi.fn()}))

vi.mock("../composables/chartDrillDown", () => ({
    useChartDrillDown: () => ({drillDown}),
}))

vi.mock("../composables/useDashboards", async (importOriginal) => ({
    ...await importOriginal<typeof import("../composables/useDashboards")>(),
    useChartGenerator: () => ({
        data: ref({total: 3, results: [{date: "2024-03-15T10:00:00.000Z", level: "INFO", total: 3}]}),
        loading: ref(false),
        generate: vi.fn(),
    }),
}))

const KsEchartStub = defineComponent({
    name: "KsEchart",
    emits: ["echarts-click"],
    setup(_, {expose}) {
        expose({getEchartsInstance: () => null})
        return () => h("div")
    },
})

const logsChart = YAML_UTILS.parse<Chart>(LOGS_CHART)!

function clickBar(selectable: boolean) {
    const wrapper = i18nMount(TimeSeries, {
        props: {chart: logsChart, showDefault: true, selectable},
        global: {plugins: [createPinia()], stubs: {KsEchart: KsEchartStub, ChartLegend: true}},
    })
    wrapper.findComponent(KsEchartStub).vm.$emit("echarts-click", {seriesType: "bar", seriesName: "INFO", dataIndex: 0})
}

describe("TimeSeries bar click", () => {
    beforeEach(() => drillDown.mockClear())

    it("leaves a click on a brush-selectable chart to the brush", async () => {
        clickBar(true)
        await flushPromises()

        expect(drillDown).not.toHaveBeenCalled()
    })

    it("drills down from a chart without the brush", async () => {
        clickBar(false)
        await flushPromises()

        expect(drillDown).toHaveBeenCalledOnce()
    })
})
