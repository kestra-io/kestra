import {beforeEach, describe, expect, it, vi} from "vitest"

const findFlows = vi.hoisted(() => vi.fn())
const user = vi.hoisted(() => ({hasAnyAction: vi.fn()}))

vi.mock("../../../src/stores/flow", () => ({useFlowStore: () => ({findFlows})}))
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({user})}))

import {shouldShowWelcome} from "../../../src/utils/welcomeGuard"

describe("shouldShowWelcome", () => {
    beforeEach(() => {
        findFlows.mockReset()
        user.hasAnyAction.mockReset().mockReturnValue(true)
    })

    it("shows the welcome when the tenant has no flow outside the tutorial", async () => {
        findFlows.mockResolvedValue(0)

        expect(await shouldShowWelcome()).toBe(true)
    })

    it("does not look for flows when the user cannot list them", async () => {
        user.hasAnyAction.mockReturnValue(false)

        expect(await shouldShowWelcome()).toBe(false)
        expect(findFlows).not.toHaveBeenCalled()
    })
})
