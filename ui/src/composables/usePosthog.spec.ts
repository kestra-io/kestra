import {beforeEach, describe, expect, it, vi} from "vitest"

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

describe("trackSetupEvent", () => {
    beforeEach(() => {
        posthogEvents.mockClear()
        miscStore.configs = {isAnonymousUsageEnabled: true, uuid: "instance-uuid"}
        uidState.value = "user-uid"
    })

    it("should send nothing when anonymous usage is disabled", async () => {
        miscStore.configs = {isAnonymousUsageEnabled: false, uuid: "instance-uuid"}

        const {trackSetupEvent} = await import("./usePosthog")
        trackSetupEvent("setup_flow:completed", {}, {username: "user@example.com"})

        expect(posthogEvents).not.toHaveBeenCalled()
    })

    it("should send nothing when no uid exists", async () => {
        uidState.value = undefined

        const {trackSetupEvent} = await import("./usePosthog")
        trackSetupEvent("setup_flow:completed", {}, {username: "user@example.com"})

        expect(posthogEvents).not.toHaveBeenCalled()
    })

    it("should forward the event type, the instance id and the user id", async () => {
        const {trackSetupEvent} = await import("./usePosthog")
        trackSetupEvent("setup_flow:completed", {}, {username: "user@example.com"})

        expect(posthogEvents).toHaveBeenCalledWith(expect.objectContaining({
            type: "setup_flow:completed",
            instance_id: "instance-uuid",
            user_id: "user-uid",
        }))
    })

    it("should attach the user info when a first name was given", async () => {
        const {trackSetupEvent} = await import("./usePosthog")
        trackSetupEvent("setup_flow:completed", {}, {
            firstName: "Ada",
            lastName: "Lovelace",
            username: "ada@example.com",
        })

        expect(posthogEvents).toHaveBeenCalledWith(expect.objectContaining({
            user_firstname: "Ada",
            user_lastname: "Lovelace",
            user_email: "ada@example.com",
        }))
    })

    it("should attach no user info when no first name was given", async () => {
        const {trackSetupEvent} = await import("./usePosthog")
        trackSetupEvent("setup_flow:completed", {}, {username: "ada@example.com"})

        const payload = posthogEvents.mock.calls[0][0]
        expect(payload).not.toHaveProperty("user_firstname")
        expect(payload).not.toHaveProperty("user_lastname")
        expect(payload).not.toHaveProperty("user_email")
    })

    it("should merge the additional data in and let it override the user info", async () => {
        const {trackSetupEvent} = await import("./usePosthog")
        trackSetupEvent("setup_flow:completed", {user_email: "override@example.com", source: "setup"}, {
            firstName: "Ada",
            lastName: "Lovelace",
            username: "ada@example.com",
        })

        expect(posthogEvents).toHaveBeenCalledWith(expect.objectContaining({
            user_firstname: "Ada",
            user_lastname: "Lovelace",
            user_email: "override@example.com",
            source: "setup",
        }))
    })
})
