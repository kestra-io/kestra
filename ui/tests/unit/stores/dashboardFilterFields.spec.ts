import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

// Avoid pulling in the full design-system (monaco-editor) on cold import.
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

const previewChartFn = vi.fn().mockResolvedValue({})
const dashboardChartDataFn = vi.fn().mockResolvedValue({})

vi.mock("@kestra-io/kestra-sdk/dashboards", () => ({
    previewChart: (...args: any[]) => previewChartFn(...args),
    dashboardChartData: (...args: any[]) => dashboardChartDataFn(...args),
}))

// The first cold import under full-suite contention can exceed the 5s default.
const TEST_TIMEOUT_MS = 20_000

describe("dashboard store QueryFilter field normalization", () => {
    beforeEach(() => {
        vi.resetModules()
        previewChartFn.mockReset().mockResolvedValue({})
        dashboardChartDataFn.mockReset().mockResolvedValue({})
        setActivePinia(createPinia())
    })

    it("converts enum-name fields to their wire form for chartPreview body", {timeout: TEST_TIMEOUT_MS}, async () => {
        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        const dashboardStore = useDashboardStore()

        await dashboardStore.chartPreview({
            chart: "id: c1",
            globalFilter: {
                filters: [
                    {field: "TIME_RANGE" as any, operation: "EQUALS", value: "PT24H"},
                    {field: "FLOW_ID" as any, operation: "EQUALS", value: "flow"},
                    {field: "NAMESPACE" as any, operation: "EQUALS", value: "io.kestra"},
                ],
            },
        } as any)

        const sentFilters = previewChartFn.mock.calls[0][0].globalFilter.filters
        expect(sentFilters.map((f: any) => f.field)).toEqual(["timeRange", "flowId", "namespace"])
    })

    it("leaves already wire-form fields untouched", {timeout: TEST_TIMEOUT_MS}, async () => {
        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        const dashboardStore = useDashboardStore()

        await dashboardStore.chartPreview({
            chart: "id: c1",
            globalFilter: {
                filters: [
                    {field: "timeRange" as any, operation: "EQUALS", value: "PT24H"},
                    {field: "flowId" as any, operation: "EQUALS", value: "flow"},
                ],
            },
        } as any)

        const sentFilters = previewChartFn.mock.calls[0][0].globalFilter.filters
        expect(sentFilters.map((f: any) => f.field)).toEqual(["timeRange", "flowId"])
    })

    it("converts fields for generate body and recurses into children", {timeout: TEST_TIMEOUT_MS}, async () => {
        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        const dashboardStore = useDashboardStore()

        await dashboardStore.generate("d1", "chart1", {
            filters: [
                {field: "STATE" as any, operation: "IN", value: ["RUNNING"]},
                {
                    logical: "OR" as any,
                    children: [{field: "FLOW_ID" as any, operation: "EQUALS", value: "flow"}],
                } as any,
            ],
        })

        const sentFilters = dashboardChartDataFn.mock.calls[0][0].filters
        expect(sentFilters[0].field).toBe("state")
        expect(sentFilters[1].children[0].field).toBe("flowId")
    })
})
