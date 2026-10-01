import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

vi.mock("@kestra-io/design-system", () => ({
    stringUtils: {afterLastDot: (s: string) => s?.split(".").pop() ?? s},
    durationUtils: {humanDuration: () => "", duration: () => 0},
    State: {},
}))

vi.mock("nprogress", () => ({start: vi.fn(), done: vi.fn(), set: vi.fn(), inc: vi.fn()}))
vi.mock("vue-router", () => ({useRouter: () => ({beforeEach: vi.fn(), afterEach: vi.fn(), replace: vi.fn(), push: vi.fn()})}))
vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}))

const searchDashboards = vi.hoisted(() => vi.fn())
const user = vi.hoisted(() => ({hasAnyAction: vi.fn()}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn().mockResolvedValue({data: {}}), post: vi.fn(), put: vi.fn(), delete: vi.fn()}),
}))
vi.mock("@kestra-io/kestra-sdk/dashboards", () => ({searchDashboards}))
vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1/main",
    apiUrlWithoutTenants: () => "/api/v1",
    basePath: () => "/ui/main",
    baseUrl: "/",
}))
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({configs: {isCustomDashboardsEnabled: true}})}))
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({user})}))

const route = {name: "home", params: {tenant: "main"}, query: {}} as any

describe("dashboard store listing", () => {
    beforeEach(() => {
        vi.resetModules()
        searchDashboards.mockReset().mockResolvedValue({results: [{id: "custom", title: "Custom"}]})
        user.hasAnyAction.mockReset()
        setActivePinia(createPinia())
    })

    it("lists the custom dashboards for a user who can list them", {timeout: 20_000}, async () => {
        user.hasAnyAction.mockReturnValue(true)
        const {useDashboardStore} = await import("../../../src/stores/dashboard")

        const dashboards = await useDashboardStore().list({}, route)

        expect(searchDashboards).toHaveBeenCalled()
        expect(dashboards.map(dashboard => dashboard.id)).toContain("custom")
    })

    it("does not search for custom dashboards when the user cannot list them, and still offers the bundled one", {timeout: 20_000}, async () => {
        user.hasAnyAction.mockReturnValue(false)
        const {useDashboardStore} = await import("../../../src/stores/dashboard")

        const dashboards = await useDashboardStore().list({}, route)

        expect(searchDashboards).not.toHaveBeenCalled()
        expect(dashboards).toHaveLength(1)
        expect(dashboards[0].isDefault).toBe(true)
    })
})
