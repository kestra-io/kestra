import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

import type {Chart, Dashboard} from "../../../src/components/dashboard/types"

const post = vi.fn()
const downloadUrl = vi.fn()

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

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post, put: vi.fn(), delete: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk/dashboards", () => ({}))

vi.mock("../../../src/utils/utils", async (importOriginal) => ({
    ...(await importOriginal<typeof import("../../../src/utils/utils")>()),
    downloadUrl,
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1/main",
    apiUrlWithoutTenants: () => "/api/v1",
    basePath: () => "/ui/main",
    baseUrl: "/",
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {isCustomDashboardsEnabled: true}}),
}))

const DASHBOARD: Dashboard = {id: "default", title: "A dashboard", deleted: false, charts: []}
const CHART: Chart = {id: "a-chart", type: "io.kestra.plugin.core.dashboard.chart.Table", content: "id: a-chart"}

describe("dashboard store chart export", () => {
    beforeEach(() => {
        vi.resetModules()
        post.mockReset()
        downloadUrl.mockReset()
        window.URL.createObjectURL = vi.fn(() => "blob:a-url")
        localStorage.clear()
        setActivePinia(createPinia())
    })

    // A chart with no rows used to answer 200 with an empty body, which the handler below refused
    // to write out. CSV now always carries its header row, so there is a file worth handing over.
    it("downloads a header-only CSV export rather than reporting it as empty", async () => {
        post.mockResolvedValue({data: "chart_namespace,chart_execution_id\r\n"})
        const {useDashboardStore} = await import("../../../src/stores/dashboard")

        const exported = await useDashboardStore().export(DASHBOARD, CHART, {}, "CSV")

        expect(exported).toBe(true)
        expect(downloadUrl).toHaveBeenCalledWith("blob:a-url", "chart__a-chart.csv")
    })

    // ION has no header concept, so an empty chart really is 0 bytes there and writing it out
    // hands the user a file that looks like a failed download.
    it("still refuses a 0 byte ION export", async () => {
        post.mockResolvedValue({data: ""})
        const {useDashboardStore} = await import("../../../src/stores/dashboard")

        const exported = await useDashboardStore().export(DASHBOARD, CHART, {}, "ION")

        expect(exported).toBe(false)
        expect(downloadUrl).not.toHaveBeenCalled()
    })

    it("downloads an ION export that carries rows", async () => {
        post.mockResolvedValue({data: "{chart_namespace:a_namespace}\n"})
        const {useDashboardStore} = await import("../../../src/stores/dashboard")

        const exported = await useDashboardStore().export(DASHBOARD, CHART, {}, "ION")

        expect(exported).toBe(true)
        expect(downloadUrl).toHaveBeenCalledWith("blob:a-url", "chart__a-chart.ion")
    })
})
