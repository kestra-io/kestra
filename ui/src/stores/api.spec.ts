import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

const publicFetch = vi.fn().mockResolvedValue(new Response())

vi.mock("nprogress", () => ({
    start: vi.fn(),
    done: vi.fn(),
    set: vi.fn(),
    inc: vi.fn(),
}))

vi.mock("vue-router", () => ({
    useRouter: () => ({
        beforeEach: vi.fn(),
        afterEach: vi.fn(),
        replace: vi.fn(),
        push: vi.fn(),
    }),
}))

vi.mock("../utils/publicApi", () => ({
    publicFetch,
    publicFetchJson: vi.fn(),
}))

const capturePosthogEvent = vi.fn()
const disablePosthog = vi.fn()

vi.mock("../utils/posthog", () => ({
    capturePosthogEvent,
    disablePosthog,
}))

vi.mock("../utils/uid", () => ({
    ensureUid: vi.fn(() => "uid-123"),
    getUid: vi.fn(() => "uid-123"),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: vi.fn(),
        post: vi.fn(),
    }),
}))

describe("api store events", () => {
    beforeEach(() => {
        vi.resetModules()
        publicFetch.mockClear()
        capturePosthogEvent.mockClear()
        disablePosthog.mockClear()
        setActivePinia(createPinia())
    })

    it("buffers events until configs are available and flushes", async () => {
        const {useApiStore} = await import("./api")
        const {useMiscStore} = await import("override/stores/misc")

        const apiStore = useApiStore()
        const miscStore = useMiscStore()

        await apiStore.events({
            type: "PAGE",
            page: {
                origin: "http://example.test",
                path: "/foo",
                fullPath: "/foo?bar=baz",
            },
            $referrer: "http://example.test/prev",
            $referring_domain: "example.test",
        })

        expect(publicFetch).not.toHaveBeenCalled()
        expect(capturePosthogEvent).not.toHaveBeenCalled()

        miscStore.configs = {
            uuid: "iid-1",
            isAnonymousUsageEnabled: true,
            isUiAnonymousUsageEnabled: true,
        }

        await apiStore.flushQueuedEvents()

        expect(publicFetch).toHaveBeenCalledTimes(1)
        const payload = publicFetch.mock.calls[0][1].body
        expect(payload.iid).toBe("iid-1")
        expect(payload.uid).toBe("uid-123")
        expect(payload.page?.origin).toBeUndefined()
        expect(payload.page?.path).toBeUndefined()
        expect(payload.page?.fullPath).toBeUndefined()
        expect(payload.$referrer).toBeUndefined()
        expect(payload.$referring_domain).toBeUndefined()
        expect(capturePosthogEvent).toHaveBeenCalledTimes(1)
    })

    it("flushes the queued events by itself once the configs arrive", async () => {
        const {useApiStore} = await import("./api")
        const {useMiscStore} = await import("override/stores/misc")

        const apiStore = useApiStore()
        const miscStore = useMiscStore()

        await apiStore.events({type: "PAGE"})
        expect(publicFetch).not.toHaveBeenCalled()

        miscStore.configs = {
            uuid: "iid-3",
            isAnonymousUsageEnabled: true,
            isUiAnonymousUsageEnabled: true,
        }

        await vi.waitFor(() => expect(publicFetch).toHaveBeenCalledTimes(1))
        expect(publicFetch.mock.calls[0][1].body.iid).toBe("iid-3")
    })

    it("drops events when analytics is disabled", async () => {
        const {useApiStore} = await import("./api")
        const {useMiscStore} = await import("override/stores/misc")

        const apiStore = useApiStore()
        const miscStore = useMiscStore()

        miscStore.configs = {
            uuid: "iid-2",
            isAnonymousUsageEnabled: false,
            isUiAnonymousUsageEnabled: false,
        }

        await apiStore.events({
            type: "PAGE",
            page: {
                origin: "http://example.test",
                path: "/foo",
                fullPath: "/foo",
            },
        })

        expect(publicFetch).not.toHaveBeenCalled()
        expect(capturePosthogEvent).not.toHaveBeenCalled()
        expect(disablePosthog).not.toHaveBeenCalled()
    })
})
