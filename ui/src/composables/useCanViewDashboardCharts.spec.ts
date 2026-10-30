import {describe, expect, it, vi} from "vitest"

const user = vi.hoisted(() => ({
    isAllowed: vi.fn((_resource: string, _action: string, namespace: string) => namespace === "allowed"),
    hasAnyAction: vi.fn(() => false),
}))
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({user})}))

import {useCanViewDashboardCharts} from "./useCanViewDashboardCharts"

describe("useCanViewDashboardCharts", () => {
    it("checks DASHBOARD.VIEW on the namespace of a namespace-scoped list", () => {
        expect(useCanViewDashboardCharts(() => "allowed").value).toBe(true)
        expect(useCanViewDashboardCharts(() => "other").value).toBe(false)
        expect(user.isAllowed).toHaveBeenCalledWith("DASHBOARD", "VIEW", "other")
    })

    it("hides the charts of a global list from a user without DASHBOARD.VIEW anywhere", () => {
        expect(useCanViewDashboardCharts(() => undefined).value).toBe(false)
        expect(user.hasAnyAction).toHaveBeenCalledWith("DASHBOARD", "VIEW")
    })
})
