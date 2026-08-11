import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

vi.mock("@kestra-io/design-system", () => ({
    stringUtils: {afterLastDot: (s: string) => s?.split(".").pop() ?? s},
    durationUtils: {humanDuration: () => "", duration: () => 0},
    State: {},
}))

vi.mock("nprogress", () => ({
    start: vi.fn(),
    done: vi.fn(),
    set: vi.fn(),
    inc: vi.fn(),
}))

vi.mock("vue-router", () => ({
    useRouter: () => ({
        beforeEach: vi.fn(),
        afterEach: vi.fn(),
        replace: vi.fn(),
        push: vi.fn(),
    }),
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))

const dashboardFn = vi.fn()
const dashboardChartDataFn = vi.fn()

vi.mock("@kestra-io/kestra-sdk/dashboards", () => ({
    dashboard: (...args: any[]) => dashboardFn(...args),
    dashboardChartData: (...args: any[]) => dashboardChartDataFn(...args),
}))

const TEST_TIMEOUT_MS = 20_000

const notFound = () => Object.assign(new Error("Not Found"), {status: 404})

describe("dashboard store dangling id handling", () => {
    beforeEach(() => {
        vi.resetModules()
        dashboardFn.mockReset()
        dashboardChartDataFn.mockReset()
        setActivePinia(createPinia())
    })

    it("opts the dashboard lookup out of global error handling", {timeout: TEST_TIMEOUT_MS}, async () => {
        dashboardFn.mockRejectedValueOnce(notFound())

        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        const dashboardStore = useDashboardStore()

        await expect(dashboardStore.load("deleted-dashboard-id")).resolves.toBeUndefined()
        expect(dashboardFn).toHaveBeenCalledWith(
            {id: "deleted-dashboard-id"},
            expect.objectContaining({showMessageOnError: false}),
        )
    })

    it("opts the chart data request out of global error handling", {timeout: TEST_TIMEOUT_MS}, async () => {
        dashboardChartDataFn.mockRejectedValueOnce(notFound())

        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        const dashboardStore = useDashboardStore()

        await expect(dashboardStore.generate("deleted-dashboard-id", "chart", {})).resolves.toBeUndefined()
        expect(dashboardChartDataFn).toHaveBeenCalledWith(
            expect.objectContaining({id: "deleted-dashboard-id", chartId: "chart"}),
            expect.objectContaining({showMessageOnError: false}),
        )
    })

    it("still propagates unexpected chart data failures", {timeout: TEST_TIMEOUT_MS}, async () => {
        const error = Object.assign(new Error("Server Error"), {status: 500})
        dashboardChartDataFn.mockRejectedValueOnce(error)

        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        const dashboardStore = useDashboardStore()

        await expect(dashboardStore.generate("d1", "chart", {})).rejects.toBe(error)
    })
})
