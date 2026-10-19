import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"

const posthogMock = {
    __loaded: false,
    capture: vi.fn(),
    opt_out_capturing: vi.fn(),
    reset: vi.fn(),
    identify: vi.fn(),
}

vi.mock("posthog-js", () => ({
    default: posthogMock,
}))

vi.mock("../composables/usePosthog", () => ({
    initPostHogForSetup: vi.fn(async () => {
        posthogMock.__loaded = true
    }),
}))

describe("posthog queue", () => {
    beforeEach(() => {
        posthogMock.__loaded = false
        posthogMock.capture.mockClear()
        posthogMock.opt_out_capturing.mockClear()
        posthogMock.reset.mockClear()
        posthogMock.identify.mockClear()
        localStorage.clear()
        vi.resetModules()
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("queues events until initialized and flushes after init", async () => {
        const {capturePosthogEvent, initPosthogIfEnabled} = await import("./posthog")

        capturePosthogEvent(
            {isUiAnonymousUsageEnabled: true},
            "test_event",
            {foo: "bar"},
        )

        expect(posthogMock.capture).not.toHaveBeenCalled()

        await initPosthogIfEnabled({isUiAnonymousUsageEnabled: true})

        expect(posthogMock.capture).toHaveBeenCalledTimes(1)
        expect(posthogMock.capture).toHaveBeenCalledWith("test_event", {foo: "bar"})
    })

    it("opts out and resets when disabled after init", async () => {
        const {capturePosthogEvent, initPosthogIfEnabled} = await import("./posthog")

        capturePosthogEvent(
            {isUiAnonymousUsageEnabled: true},
            "test_event",
            {foo: "bar"},
        )

        await initPosthogIfEnabled({isUiAnonymousUsageEnabled: true})

        capturePosthogEvent(
            {isUiAnonymousUsageEnabled: false},
            "test_event_2",
            {foo: "baz"},
        )

        expect(posthogMock.opt_out_capturing).toHaveBeenCalled()
        expect(posthogMock.reset).toHaveBeenCalled()
    })
})

describe("isPosthogEnabled", () => {
    beforeEach(() => {
        localStorage.clear()
        vi.resetModules()
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("returns false when anonymous usage is disabled in the config", async () => {
        const {isPosthogEnabled} = await import("./posthog")

        expect(isPosthogEnabled({isUiAnonymousUsageEnabled: false})).toBe(false)
    })

    it("returns true when the config enables it", async () => {
        const {isPosthogEnabled} = await import("./posthog")

        expect(isPosthogEnabled({isUiAnonymousUsageEnabled: true})).toBe(true)
    })

    it("returns false when the config is missing entirely rather than defaulting to on", async () => {
        const {isPosthogEnabled} = await import("./posthog")

        expect(isPosthogEnabled(undefined)).toBe(false)
        expect(isPosthogEnabled()).toBe(false)
    })

    it("returns true when the key is absent from the config object", async () => {
        const {isPosthogEnabled} = await import("./posthog")

        expect(isPosthogEnabled({})).toBe(true)
    })

    it("does not throw when the posthog global is absent", async () => {
        const {isPosthogEnabled} = await import("./posthog")

        expect(() => isPosthogEnabled({isUiAnonymousUsageEnabled: true})).not.toThrow()
    })
})

describe("identifyPosthogUser", () => {
    beforeEach(() => {
        posthogMock.__loaded = false
        posthogMock.capture.mockClear()
        posthogMock.opt_out_capturing.mockClear()
        posthogMock.reset.mockClear()
        posthogMock.identify.mockClear()
        localStorage.clear()
        vi.resetModules()
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("passes the expected identifier and traits through", async () => {
        localStorage.setItem("uid", "user-identifier-123")
        const {identifyPosthogUser, initPosthogIfEnabled} = await import("./posthog")

        await initPosthogIfEnabled({isUiAnonymousUsageEnabled: true})
        await identifyPosthogUser({isUiAnonymousUsageEnabled: true}, {userId: "u1", plan: "free"})

        expect(posthogMock.identify).toHaveBeenCalledTimes(1)
        expect(posthogMock.identify).toHaveBeenCalledWith(
            "user-identifier-123",
            {userId: "u1", plan: "free"},
        )
    })

    it("does nothing when analytics are disabled", async () => {
        const {identifyPosthogUser, initPosthogIfEnabled} = await import("./posthog")
        await initPosthogIfEnabled({isUiAnonymousUsageEnabled: true})

        await identifyPosthogUser({isUiAnonymousUsageEnabled: false}, {userId: "u1"})

        expect(posthogMock.identify).not.toHaveBeenCalled()
        expect(posthogMock.opt_out_capturing).toHaveBeenCalledTimes(1)
    })

    it("does not throw when the posthog client has no identify method", async () => {
        const savedIdentify = posthogMock.identify
        // @ts-expect-error — deliberately removing identify to exercise the !client?.identify guard
        delete posthogMock.identify

        const {identifyPosthogUser, initPosthogIfEnabled} = await import("./posthog")
        await initPosthogIfEnabled({isUiAnonymousUsageEnabled: true})

        await expect(
            identifyPosthogUser({isUiAnonymousUsageEnabled: true}, {foo: "bar"}),
        ).resolves.toBeUndefined()

        posthogMock.identify = savedIdentify
    })
})
