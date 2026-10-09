import {beforeEach, describe, expect, it, vi} from "vitest"
import {isDashboardRoute, shouldShowWelcome} from "./welcomeGuard"
import {TUTORIAL_NAMESPACE} from "./constants"

const {findFlows, user} = vi.hoisted(() => ({findFlows: vi.fn(), user: {hasAnyAction: vi.fn()}}))
vi.mock("../stores/flow", () => ({useFlowStore: () => ({findFlows})}))
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({user})}))

describe("isDashboardRoute", () => {
    it("matches only the home route", () => {
        expect(isDashboardRoute("home")).toBe(true)
        expect(isDashboardRoute("flows/list")).toBe(false)
    })
})

describe("shouldShowWelcome", () => {
    beforeEach(() => {
        findFlows.mockReset()
        user.hasAnyAction.mockReset().mockReturnValue(true)
    })

    it("shows the welcome page when no flow exists outside the tutorial namespace", async () => {
        findFlows.mockResolvedValue(0)

        expect(await shouldShowWelcome()).toBe(true)
    })

    it("skips the welcome page when a flow exists outside the tutorial namespace", async () => {
        findFlows.mockResolvedValue(1)

        expect(await shouldShowWelcome()).toBe(false)
    })

    it("does not look for flows when the user cannot list them", async () => {
        user.hasAnyAction.mockReturnValue(false)

        expect(await shouldShowWelcome()).toBe(false)
        expect(findFlows).not.toHaveBeenCalled()
    })

    it("counts only the flows outside the tutorial namespace", async () => {
        findFlows.mockResolvedValue(0)

        await shouldShowWelcome()

        expect(findFlows).toHaveBeenCalledWith({
            size: 1,
            onlyTotal: true,
            "filters[namespace][NOT_EQUALS]": TUTORIAL_NAMESPACE,
        })
    })
})
