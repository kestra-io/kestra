import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {dateUtils} from "@kestra-io/design-system"
import {i18nShallowMount} from "../../../../tests/unit/i18nMount"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))

vi.mock("../composables/chartDrillDown", () => ({
    useChartDrillDown: () => ({drillDown: vi.fn()}),
}))

import Pie from "./Pie.vue"
import {useDashboardStore} from "../../../stores/dashboard"

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
        vi.spyOn(useDashboardStore(), "chartPreview").mockResolvedValue({
            results: [{dim: "A", count_col: 1000000}, {dim: "B", count_col: 234567}],
        } as never)
        const wrapper = i18nShallowMount(Pie, {
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
        await flushPromises()
        expect(wrapper.text()).toContain("1.234.567")
    })

    it("groups date values by their day in the timezone from Settings", async () => {
        localStorage.setItem(dateUtils.TIMEZONE_STORAGE_KEY, "Pacific/Kiritimati")
        vi.spyOn(useDashboardStore(), "chartPreview").mockResolvedValue({
            results: [{dim: "2026-11-09T12:00:00.000Z", count_col: 3}],
        } as never)
        const wrapper = i18nShallowMount(Pie, {
            props: {showDefault: true, chart},
            global: {
                stubs: {
                    KsPie: {props: ["data"], template: "<div data-test=\"pie\">{{ data.map(item => item.name).join() }}</div>"},
                    ChartLegend: {template: "<div />", inheritAttrs: false},
                },
            },
        })
        await flushPromises()
        expect(wrapper.find("[data-test=pie]").text()).toBe("2026-11-10")
    })
})
