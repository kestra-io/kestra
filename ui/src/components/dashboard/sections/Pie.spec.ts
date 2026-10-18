import {afterEach, describe, expect, it, vi} from "vitest"
import {ref} from "vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {storageKeys} from "../../../utils/constants"

vi.mock("../composables/useDashboards", () => ({
    useChartGenerator: () => ({
        data: ref({results: [{state: "SUCCESS", count: 1_000_000}, {state: "FAILED", count: 234_567}]}),
        loading: ref(false),
        generate: vi.fn(),
    }),
}))

vi.mock("../composables/chartDrillDown", () => ({
    useChartDrillDown: () => ({drillDown: vi.fn()}),
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {}, query: {}}),
}))

import Pie from "./Pie.vue"

const EMPTY_STUB = {inheritAttrs: false, template: "<div/>"}

const mountPie = () =>
    i18nMount(Pie, {
        props: {
            chart: {
                id: "executions-per-state",
                type: "io.kestra.plugin.core.dashboard.chart.Pie",
                data: {
                    type: "io.kestra.plugin.core.dashboard.data.Executions",
                    columns: {state: {field: "STATE"}, count: {field: "ID", agg: "COUNT"}},
                },
            },
        },
        global: {stubs: {KsPie: EMPTY_STUB, ChartLegend: EMPTY_STUB}},
    })

describe("Pie center total", () => {
    afterEach(() => localStorage.clear())

    it("formats the total in the language chosen in settings, not the browser's", () => {
        localStorage.setItem(storageKeys.LANG, "de")

        expect(mountPie().get(".pie-center-label__total").text()).toBe("1.234.567")
    })
})
