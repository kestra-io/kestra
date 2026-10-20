import {beforeEach, describe, expect, it, vi} from "vitest"
import {useRoute} from "vue-router"
import {
    TOUR_ANALYTICS_EXPERIENCE,
    TOUR_ANALYTICS_VERSION,
    useOnboardingAnalytics,
} from "./useOnboardingAnalytics"

const {events, pageFromRoute} = vi.hoisted(() => ({
    events: vi.fn(),
    pageFromRoute: vi.fn(),
}))

vi.mock("vue-router", () => ({useRoute: vi.fn()}))
vi.mock("../stores/api", () => ({useApiStore: () => ({events})}))
vi.mock("../utils/eventsRouter", () => ({pageFromRoute}))

const SCENE_IDS = ["welcome", "create-flow", "run-flow"]
const GUIDE_ID = "guide-1"
const ROUTE = {name: "flows/list", path: "/main/flows"}
const PAGE = {path: "/flows", title: "Flows"}

function setup() {
    return useOnboardingAnalytics({sceneIds: SCENE_IDS, guideId: GUIDE_ID}).trackOnboarding
}

function sentPayload() {
    expect(events).toHaveBeenCalledTimes(1)
    return events.mock.calls[0][0]
}

describe("useOnboardingAnalytics", () => {
    beforeEach(() => {
        events.mockReset()
        pageFromRoute.mockReset()
        pageFromRoute.mockReturnValue(PAGE)
        vi.mocked(useRoute).mockReturnValue(ROUTE as never)
    })

    it("sends an ONBOARDING event with the version and experience constants", () => {
        setup()({event: "tour_started"})

        const payload = sentPayload()
        expect(payload.type).toBe("ONBOARDING")
        expect(payload.onboarding.version).toBe(TOUR_ANALYTICS_VERSION)
        expect(payload.onboarding.experience).toBe(TOUR_ANALYTICS_EXPERIENCE)
    })

    it.each([
        ["welcome", 1],
        ["create-flow", 2],
        ["run-flow", 3],
    ])("sets step to the 1-based index of %s in sceneIds", (action, expectedStep) => {
        setup()({event: "tour_continued", action})

        expect(sentPayload().onboarding.step).toBe(expectedStep)
    })

    it("leaves step undefined for an action that is not a scene id", () => {
        setup()({event: "tour_continued", action: "not-a-scene"})

    
        expect(sentPayload().onboarding.step).toBeUndefined()
    })

    it("leaves step undefined when no action is given", () => {
        setup()({event: "tour_offered"})

        expect(sentPayload().onboarding.step).toBeUndefined()
    })

    it("forwards guideId, event, action and mode", () => {
        setup()({event: "tour_continued", action: "create-flow", mode: "guided"})

        expect(sentPayload().onboarding).toMatchObject({
            guideId: GUIDE_ID,
            event: "tour_continued",
            action: "create-flow",
            mode: "guided",
        })
    expect(sentPayload().onboarding.template).toBe(TOUR_ANALYTICS_EXPERIENCE)
        })
    

    it("merges the additional fields into the onboarding payload", () => {
        setup()({event: "tour_closed", additional: {reason: "skipped", durationMs: 1200}})

        const onboarding = sentPayload().onboarding
        expect(onboarding.reason).toBe("skipped")
        expect(onboarding.durationMs).toBe(1200)
        expect(onboarding.event).toBe("tour_closed")
        expect(onboarding.guideId).toBe(GUIDE_ID)
    })

    it("carries the current page taken from the route", () => {
        setup()({event: "tour_started"})

        expect(pageFromRoute).toHaveBeenCalledWith(ROUTE)
        expect(sentPayload().page).toBe(PAGE)
    })
})