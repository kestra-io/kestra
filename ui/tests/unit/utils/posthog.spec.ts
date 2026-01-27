import {describe, it, expect, vi, beforeEach} from "vitest";

const posthogMock = {
    __loaded: false,
    capture: vi.fn(),
    opt_out_capturing: vi.fn(),
    reset: vi.fn(),
};

vi.mock("posthog-js", () => ({
    default: posthogMock,
}));

vi.mock("../../../src/composables/usePosthog", () => ({
    initPostHogForSetup: vi.fn(async () => {
        posthogMock.__loaded = true;
    }),
}));

const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0));

describe("posthog queue", () => {
    beforeEach(() => {
        posthogMock.__loaded = false;
        posthogMock.capture.mockClear();
        posthogMock.opt_out_capturing.mockClear();
        posthogMock.reset.mockClear();
        vi.resetModules();
    });

    it("queues events until initialized and flushes after init", async () => {
        const {capturePosthogEvent} = await import("../../../src/utils/posthog");

        capturePosthogEvent(
            {isUiAnonymousUsageEnabled: true},
            "test_event",
            {foo: "bar"}
        );

        expect(posthogMock.capture).not.toHaveBeenCalled();

        await flushPromises();
        await flushPromises();

        expect(posthogMock.capture).toHaveBeenCalledTimes(1);
        expect(posthogMock.capture).toHaveBeenCalledWith("test_event", {foo: "bar"});
    });

    it("opts out and resets when disabled after init", async () => {
        const {capturePosthogEvent} = await import("../../../src/utils/posthog");

        capturePosthogEvent(
            {isUiAnonymousUsageEnabled: true},
            "test_event",
            {foo: "bar"}
        );

        await flushPromises();
        await flushPromises();

        capturePosthogEvent(
            {isUiAnonymousUsageEnabled: false},
            "test_event_2",
            {foo: "baz"}
        );

        expect(posthogMock.opt_out_capturing).toHaveBeenCalled();
        expect(posthogMock.reset).toHaveBeenCalled();
    });
});
