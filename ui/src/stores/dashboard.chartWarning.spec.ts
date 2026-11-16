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

const post = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post, put: vi.fn(), delete: vi.fn()}),
    // Stands in for the real helper: recognises a problem document the SDK flattened onto the Error.
    asProblem: (error: Record<string, unknown>) => (error?.type ? {type: error.type, detail: error.detail, status: error.status} : undefined),
}))

vi.mock("@kestra-io/kestra-sdk/dashboards", () => ({}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1/main",
    apiUrlWithoutTenants: () => "/api/v1",
    basePath: () => "/ui/main",
    baseUrl: "/",
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {isCustomDashboardsEnabled: true}}),
}))

const TEST_TIMEOUT_MS = 20_000

describe("dashboard store chart data", () => {
    beforeEach(() => {
        vi.resetModules()
        post.mockReset()
        localStorage.clear()
        setActivePinia(createPinia())
    })

    it("turns a chart whose data is gone into a warning carrying the server's message", {timeout: TEST_TIMEOUT_MS}, async () => {
        const detail = "The file of the output 'rejected' of the execution 'e1' of 2026-11-09T10:00:00Z was deleted by the retention."
        post.mockRejectedValue(Object.assign(new Error("Gone"), {status: 410, type: "https://kestra.io/docs/api-reference/problems/resource-expired", detail}))
        const {useDashboardStore} = await import("./dashboard")

        const data = await useDashboardStore().generate("orders", "rejected_table", {filters: []})

        expect(data).toEqual({results: [], total: 0, warning: detail})
    })

    it("turns a chart whose output file is too large into a warning carrying the server's message", {timeout: TEST_TIMEOUT_MS}, async () => {
        const detail = "The output 'rejected' is 963416800 bytes, which is more than the 104857600 bytes a chart can read."
        post.mockRejectedValue(Object.assign(new Error("Bad Request"), {status: 400, type: "https://kestra.io/docs/api-reference/problems/bad-request", detail}))
        const {useDashboardStore} = await import("./dashboard")

        const data = await useDashboardStore().generate("orders", "rejected_table", {filters: []})

        expect(data).toEqual({results: [], total: 0, warning: detail})
    })

    it("still rejects when the chart fails for another reason", {timeout: TEST_TIMEOUT_MS}, async () => {
        post.mockRejectedValue(Object.assign(new Error("Server error"), {status: 500}))
        const {useDashboardStore} = await import("./dashboard")

        await expect(useDashboardStore().generate("orders", "rejected_table", {filters: []})).rejects.toThrow("Server error")
    })
})
