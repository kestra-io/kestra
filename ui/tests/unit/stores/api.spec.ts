import {describe, it, expect, vi, beforeEach} from "vitest";
import {setActivePinia, createPinia} from "pinia";

const axiosPost = vi.fn().mockResolvedValue({data: {}});

vi.mock("nprogress", () => ({
    start: vi.fn(),
    done: vi.fn(),
    set: vi.fn(),
    inc: vi.fn(),
}));

vi.mock("vue-router", () => ({
    useRouter: () => ({
        beforeEach: vi.fn(),
        afterEach: vi.fn(),
        replace: vi.fn(),
        push: vi.fn(),
    }),
}));

vi.mock("axios", () => ({
    default: {
        post: axiosPost,
        get: vi.fn(),
    },
}));

const capturePosthogEvent = vi.fn();
const disablePosthog = vi.fn();

vi.mock("../../../src/utils/posthog", () => ({
    capturePosthogEvent,
    disablePosthog,
}));

vi.mock("../../../src/utils/uid", () => ({
    ensureUid: vi.fn(() => "uid-123"),
    getUid: vi.fn(() => "uid-123"),
}));

vi.mock("../../../src/utils/axios", () => ({
    useAxios: () => ({
        get: vi.fn(),
        post: vi.fn(),
    }),
}));

describe("api store events", () => {
    beforeEach(() => {
        vi.resetModules();
        axiosPost.mockClear();
        capturePosthogEvent.mockClear();
        disablePosthog.mockClear();
        setActivePinia(createPinia());
        localStorage.clear();
    });

    it("buffers events until configs are available and flushes", async () => {
        const {useApiStore} = await import("../../../src/stores/api");
        const {useMiscStore} = await import("override/stores/misc");

        const apiStore = useApiStore();
        const miscStore = useMiscStore();

        await apiStore.events({
            type: "PAGE",
            page: {
                origin: "http://example.test",
                path: "/foo",
                fullPath: "/foo?bar=baz",
            },
            $referrer: "http://example.test/prev",
            $referring_domain: "example.test",
        });

        expect(axiosPost).not.toHaveBeenCalled();
        expect(capturePosthogEvent).not.toHaveBeenCalled();

        miscStore.configs = {
            uuid: "iid-1",
            isAnonymousUsageEnabled: true,
            isUiAnonymousUsageEnabled: true,
        };

        await apiStore.flushQueuedEvents();

        expect(axiosPost).toHaveBeenCalledTimes(1);
        const payload = axiosPost.mock.calls[0][1];
        expect(payload.iid).toBe("iid-1");
        expect(payload.uid).toBe("uid-123");
        expect(payload.page?.origin).toBeUndefined();
        expect(payload.page?.path).toBeUndefined();
        expect(payload.page?.fullPath).toBeUndefined();
        expect(payload.$referrer).toBeUndefined();
        expect(payload.$referring_domain).toBeUndefined();
        expect(capturePosthogEvent).toHaveBeenCalledTimes(1);
    });

    it("drops events when analytics is disabled", async () => {
        const {useApiStore} = await import("../../../src/stores/api");
        const {useMiscStore} = await import("override/stores/misc");

        const apiStore = useApiStore();
        const miscStore = useMiscStore();

        miscStore.configs = {
            uuid: "iid-2",
            isAnonymousUsageEnabled: false,
            isUiAnonymousUsageEnabled: false,
        };

        await apiStore.events({
            type: "PAGE",
            page: {
                origin: "http://example.test",
                path: "/foo",
                fullPath: "/foo",
            },
        });

        expect(axiosPost).not.toHaveBeenCalled();
        expect(capturePosthogEvent).not.toHaveBeenCalled();
        expect(disablePosthog).not.toHaveBeenCalled();
    });
});
