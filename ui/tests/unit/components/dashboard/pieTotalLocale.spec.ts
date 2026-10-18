import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {i18nShallowMount} from "../../i18nMount"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))

vi.mock("../../../../src/components/dashboard/composables/chartDrillDown", () => ({
    useChartDrillDown: () => ({drillDown: vi.fn()}),
}))

import Pie from "../../../../src/components/dashboard/sections/Pie.vue"
import {useDashboardStore} from "../../../../src/stores/dashboard"

const chart = {
    id: "pie-locale",
    content: "charts:\n  - id: pie-locale",
    chartOptions: {graphStyle: "PIE"},
    data: {
        columns: {
            dim: {displayName: "Dimension"},
            count_col: {agg: "SUM", field: "COUNT", displayName: "Count"},
        },
    },
} as never

const results = [
    {dim: "A", count_col: 1000000},
    {dim: "B", count_col: 234567},
]

function mountPie() {
    const store = useDashboardStore()
    vi.spyOn(store, "chartPreview").mockResolvedValue({results} as never)
    return i18nShallowMount(Pie, {
        props: {showDefault: true, chart},
        global: {
            stubs: {
                KsPie: {template: "<div />", inheritAttrs: false},
                KsSkeleton: {template: "<div />", inheritAttrs: false},
                KsNoData: {template: "<div />", inheritAttrs: false},
                ChartLegend: {template: "<div />", inheritAttrs: false},
            },
        },
    })
}

describe("dashboard Pie total", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        localStorage.clear()
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("formats the center total with the app locale, not the runtime locale", async () => {
        localStorage.setItem("lang", "de")
        const wrapper = mountPie()
        await flushPromises()
        expect(wrapper.find(".pie-center-label__total").text()).toBe("1.234.567")
    })

    it("keeps the en grouping when the app locale is en", async () => {
        localStorage.setItem("lang", "en")
        const wrapper = mountPie()
        await flushPromises()
        expect(wrapper.find(".pie-center-label__total").text()).toBe("1,234,567")
    })
})
