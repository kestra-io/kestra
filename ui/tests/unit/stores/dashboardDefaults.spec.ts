import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

vi.mock("@kestra-io/design-system", () => ({
    stringUtils: {afterLastDot: (value: string) => value?.split(".").pop() ?? value},
    durationUtils: {humanDuration: () => "", duration: () => 0},
    State: {},
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
    useClient: () => ({
        get: vi.fn(),
        post: vi.fn(),
    }),
}))

const defaultDashboards = vi.fn()

vi.mock("@kestra-io/kestra-sdk/dashboards-admin", () => ({
    defaultDashboards: (...args: any[]) => defaultDashboards(...args),
}))

vi.mock("@kestra-io/kestra-sdk/dashboards", () => ({}))
vi.mock("@kestra-io/kestra-sdk/tenants", () => ({}))

describe("dashboard defaults", () => {
    beforeEach(() => {
        vi.resetModules()
        defaultDashboards.mockReset()
        setActivePinia(createPinia())
        localStorage.clear()
    })

    it("falls back to the bundled flow dashboard when defaults are unavailable", async () => {
        defaultDashboards.mockRejectedValueOnce(Object.assign(new Error("Not Found"), {status: 404}))

        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        const dashboardStore = useDashboardStore()
        const route = {
            name: "flows/update",
            params: {tenant: "main", namespace: "tutorial", id: "hello-world", tab: "overview"},
            query: {"filters[timeRange][EQUALS]": "PT24H"},
        }

        await expect(dashboardStore.getDashboardId(route as any)).resolves.toBe("default")
        expect(defaultDashboards).toHaveBeenCalledWith(undefined, {showMessageOnError: false})
    })

    it("does not hide unexpected errors while loading defaults", async () => {
        const error = Object.assign(new Error("Server Error"), {status: 500})
        defaultDashboards.mockRejectedValueOnce(error)

        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        const dashboardStore = useDashboardStore()

        await expect(dashboardStore.loadDefaults()).rejects.toBe(error)
    })
})
