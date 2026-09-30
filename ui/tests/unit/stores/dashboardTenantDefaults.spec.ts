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

const get = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get, post: vi.fn(), put: vi.fn(), delete: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk/dashboards", () => ({}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1/main",
    apiUrlWithoutTenants: () => "/api/v1",
    basePath: () => "/ui/main",
    baseUrl: "/",
}))

let isCustomDashboardsEnabled: boolean | undefined

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {isCustomDashboardsEnabled}}),
}))

const TEST_TIMEOUT_MS = 20_000

const route = {name: "home", params: {tenant: "main"}, query: {}} as any

describe("dashboard store tenant defaults", () => {
    beforeEach(() => {
        vi.resetModules()
        get.mockReset()
        localStorage.clear()
        setActivePinia(createPinia())
    })

    it("does not ask for tenant defaults when the instance cannot store dashboards", {timeout: TEST_TIMEOUT_MS}, async () => {
        isCustomDashboardsEnabled = false

        const {useDashboardStore} = await import("../../../src/stores/dashboard")

        await expect(useDashboardStore().getDashboardId(route)).resolves.toBe("default")
        expect(get).not.toHaveBeenCalled()
    })

    it("asks for tenant defaults when the instance can store dashboards", {timeout: TEST_TIMEOUT_MS}, async () => {
        isCustomDashboardsEnabled = true
        get.mockResolvedValue({data: {defaultHomeDashboard: "tenant-default"}})

        const {useDashboardStore} = await import("../../../src/stores/dashboard")

        await expect(useDashboardStore().getDashboardId(route)).resolves.toBe("tenant-default")
        expect(get).toHaveBeenCalledWith("/api/v1/main/dashboards/settings/default-dashboards")
    })
})
