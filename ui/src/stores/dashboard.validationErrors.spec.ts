import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

vi.mock("@kestra-io/design-system", () => ({
    stringUtils: {afterLastDot: (s: string) => s?.split(".").pop() ?? s},
    durationUtils: {humanDuration: () => "", duration: () => 0},
    State: {},
}))

vi.mock("nprogress", () => ({start: vi.fn(), done: vi.fn(), set: vi.fn(), inc: vi.fn()}))

vi.mock("vue-router", () => ({
    useRouter: () => ({beforeEach: vi.fn(), afterEach: vi.fn(), replace: vi.fn(), push: vi.fn()}),
}))

vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}))

const post = vi.fn()
const dashboard = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post, put: vi.fn(), delete: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk/dashboards", () => ({dashboard}))

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
const invalidChartId = {detail: "must match \"^[a-zA-Z0-9][a-zA-Z0-9_-]*\"", pointer: "/charts/0/id", path: "charts[0].id"}

function deferred() {
    let resolve: (value: {data: {errors: unknown[]}}) => void = () => {}
    const promise = new Promise<{data: {errors: unknown[]}}>(r => resolve = r)
    return {promise, resolve}
}

describe("dashboard store validation errors", () => {
    beforeEach(() => {
        vi.resetModules()
        post.mockReset()
        dashboard.mockReset()
        localStorage.clear()
        setActivePinia(createPinia())
    })

    it("keeps the located errors and their lines from the latest validation", {timeout: TEST_TIMEOUT_MS}, async () => {
        const {useDashboardStore} = await import("./dashboard")
        const store = useDashboardStore()
        post.mockResolvedValueOnce({data: {errors: [invalidChartId]}})

        await store.validateDashboard("id: a")

        expect(store.validationErrors).toEqual([invalidChartId])
        expect(store.errors).toEqual([`charts[0].id: ${invalidChartId.detail}`])
    })

    it("ignores a validation that resolves after a newer one", {timeout: TEST_TIMEOUT_MS}, async () => {
        const {useDashboardStore} = await import("./dashboard")
        const store = useDashboardStore()
        const older = deferred()
        post.mockReturnValueOnce(older.promise).mockResolvedValueOnce({data: {errors: []}})

        const pending = store.validateDashboard("id: old")
        await store.validateDashboard("id: new")
        older.resolve({data: {errors: [invalidChartId]}})
        await pending

        expect(store.validationErrors).toEqual([])
        expect(store.errors).toBeUndefined()
    })

    it("drops the errors when the latest validation fails", {timeout: TEST_TIMEOUT_MS}, async () => {
        const {useDashboardStore} = await import("./dashboard")
        const store = useDashboardStore()
        post.mockResolvedValueOnce({data: {errors: [invalidChartId]}}).mockRejectedValueOnce(new Error("boom"))

        await store.validateDashboard("id: a")
        await expect(store.validateDashboard("id: b")).rejects.toThrow("boom")

        expect(store.validationErrors).toBeUndefined()
        expect(store.errors).toBeUndefined()
    })

    it("drops the errors of the previous dashboard when another one loads", {timeout: TEST_TIMEOUT_MS}, async () => {
        const {useDashboardStore} = await import("./dashboard")
        const store = useDashboardStore()
        const inFlight = deferred()
        post.mockResolvedValueOnce({data: {errors: [invalidChartId]}}).mockReturnValueOnce(inFlight.promise)
        dashboard.mockResolvedValue({id: "b", sourceCode: "id: b"})

        await store.validateDashboard("id: a")
        const pending = store.validateDashboard("id: a")
        await store.load("b")
        inFlight.resolve({data: {errors: [invalidChartId]}})
        await pending

        expect(store.validationErrors).toBeUndefined()
        expect(store.errors).toBeUndefined()
    })
})
