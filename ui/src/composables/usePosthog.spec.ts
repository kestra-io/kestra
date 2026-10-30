import {beforeEach, describe, expect, it, vi} from "vitest"
import {trackSetupEvent} from "./usePosthog"

const {posthogEvents, miscStore, uidState} = vi.hoisted(() => ({
    posthogEvents: vi.fn(),
    miscStore: {configs: undefined as {isAnonymousUsageEnabled?: boolean, uuid?: string} | undefined},
    uidState: {value: undefined as string | undefined},
}))

vi.mock("../stores/api", () => ({
    useApiStore: () => ({posthogEvents}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => miscStore,
}))

vi.mock("../utils/uid", () => ({
    getUid: () => uidState.value,
    ensureUid: () => "user-uid",
}))

function sentPayload() {
    expect(posthogEvents).toHaveBeenCalledOnce()
    return posthogEvents.mock.calls[0][0]
}

describe("trackSetupEvent", () => {
    beforeEach(() => {
        posthogEvents.mockReset()
        miscStore.configs = {isAnonymousUsageEnabled: true, uuid: "instance-uuid"}
        uidState.value = "user-uid"
    })

    it("should send nothing when anonymous usage is disabled", () => {
        miscStore.configs = {isAnonymousUsageEnabled: false, uuid: "instance-uuid"}

        trackSetupEvent("setup_flow:completed", {}, {username: "user@example.com"})

        expect(posthogEvents).not.toHaveBeenCalled()
    })

    it("should send nothing when no uid exists", () => {
        uidState.value = undefined

        trackSetupEvent("setup_flow:completed", {}, {username: "user@example.com"})

        expect(posthogEvents).not.toHaveBeenCalled()
    })

    it("should forward the event type, the instance id and the user id", () => {
        trackSetupEvent("setup_flow:completed", {}, {username: "user@example.com"})

        expect(sentPayload()).toMatchObject({
            type: "setup_flow:completed",
            instance_id: "instance-uuid",
            user_id: "user-uid",
        })
    })

    it("should attach the user info when a first name was given", () => {
        trackSetupEvent("setup_flow:completed", {}, {
            firstName: "Ada",
            lastName: "Lovelace",
            username: "ada@example.com",
        })

        expect(sentPayload()).toMatchObject({
            user_firstname: "Ada",
            user_lastname: "Lovelace",
            user_email: "ada@example.com",
        })
    })

    it("should attach no user info when no first name was given", () => {
        trackSetupEvent("setup_flow:completed", {}, {username: "ada@example.com"})

        const payload = sentPayload()
        expect(payload).not.toHaveProperty("user_firstname")
        expect(payload).not.toHaveProperty("user_lastname")
        expect(payload).not.toHaveProperty("user_email")
    })

    it("should merge the additional data in and let it override the user info", () => {
        trackSetupEvent("setup_flow:completed", {user_email: "override@example.com", source: "setup"}, {
            firstName: "Ada",
            lastName: "Lovelace",
            username: "ada@example.com",
        })

        expect(sentPayload()).toMatchObject({
            user_firstname: "Ada",
            user_lastname: "Lovelace",
            user_email: "override@example.com",
            source: "setup",
        })
    })
})
