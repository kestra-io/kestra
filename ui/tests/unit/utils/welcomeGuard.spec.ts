import {beforeEach, describe, expect, it, vi} from "vitest"
import {isDashboardRoute, shouldShowWelcome} from "../../../src/utils/welcomeGuard"
import {TUTORIAL_NAMESPACE} from "../../../src/utils/constants"

const {findFlows} = vi.hoisted(() => ({findFlows: vi.fn()}))
vi.mock("../../../src/stores/flow", () => ({useFlowStore: () => ({findFlows})}))

describe("isDashboardRoute", () => {
    it("matches only the home route", () => {
        expect(isDashboardRoute("home")).toBe(true)
        expect(isDashboardRoute("flows/list")).toBe(false)
    })
})

describe("shouldShowWelcome", () => {
    beforeEach(() => {
        findFlows.mockReset()
    })

    it("shows the welcome page when no flow exists outside the tutorial namespace", async () => {
        findFlows.mockResolvedValue(0)

        expect(await shouldShowWelcome()).toBe(true)
    })

    it("skips the welcome page when a flow exists outside the tutorial namespace", async () => {
        findFlows.mockResolvedValue(1)

        expect(await shouldShowWelcome()).toBe(false)
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
