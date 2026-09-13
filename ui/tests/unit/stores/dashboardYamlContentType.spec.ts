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
const put = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post, put, delete: vi.fn()}),
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

describe("dashboard store yaml writes", () => {
    beforeEach(() => {
        vi.resetModules()
        post.mockReset()
        put.mockReset()
        post.mockResolvedValue({data: {}})
        put.mockResolvedValue({data: {}})
        localStorage.clear()
        setActivePinia(createPinia())
    })

    const yamlContentType = (call: any[]) => call[2]?.headers?.["Content-Type"]

    it("sends application/x-yaml when creating", {timeout: TEST_TIMEOUT_MS}, async () => {
        const {useDashboardStore} = await import("../../../src/stores/dashboard")

        await useDashboardStore().create("id: a\ntitle: A")

        expect(post).toHaveBeenCalledOnce()
        expect(post.mock.calls[0][0]).toBe("/api/v1/main/dashboards")
        expect(yamlContentType(post.mock.calls[0])).toBe("application/x-yaml")
    })

    it("sends application/x-yaml when updating", {timeout: TEST_TIMEOUT_MS}, async () => {
        const {useDashboardStore} = await import("../../../src/stores/dashboard")

        await useDashboardStore().update({id: "a", source: "id: a\ntitle: A"})

        expect(put).toHaveBeenCalledOnce()
        expect(put.mock.calls[0][0]).toBe("/api/v1/main/dashboards/a")
        expect(yamlContentType(put.mock.calls[0])).toBe("application/x-yaml")
    })

    it("sends application/x-yaml when validating a dashboard and a chart", {timeout: TEST_TIMEOUT_MS}, async () => {
        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        const store = useDashboardStore()

        await store.validateDashboard("id: a\ntitle: A")
        await store.validateChart("id: c")

        expect(post.mock.calls.map((call) => call[0])).toEqual([
            "/api/v1/main/dashboards/validate",
            "/api/v1/main/dashboards/validate/chart",
        ])
        expect(post.mock.calls.map(yamlContentType)).toEqual(["application/x-yaml", "application/x-yaml"])
    })
})
