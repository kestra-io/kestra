import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

const axiosGet = vi.fn()
const axiosPost = vi.fn().mockResolvedValue({data: {}})

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: axiosGet,
        post: axiosPost,
    }),
}))

const initPosthogIfEnabled = vi.fn()
const capturePosthogEvent = vi.fn()
const disablePosthog = vi.fn()

vi.mock("../../../src/utils/posthog", () => ({
    initPosthogIfEnabled,
    capturePosthogEvent,
    disablePosthog,
}))

vi.mock("../../../src/utils/uid", () => ({
    ensureUid: vi.fn(() => "uid-123"),
    getUid: vi.fn(() => "uid-123"),
}))

describe("misc store addBasicAuth", () => {
    beforeEach(() => {
        vi.resetModules()
        axiosGet.mockReset()
        axiosPost.mockClear()
        initPosthogIfEnabled.mockClear()
        capturePosthogEvent.mockClear()
        disablePosthog.mockClear()
        setActivePinia(createPinia())
    })

    it("loads the full (now-authenticated) configs after the basicAuth POST succeeds, and uses them for analytics", async () => {
        axiosGet.mockResolvedValue({
            data: {isBasicAuthInitialized: true, isUiAnonymousUsageEnabled: true, uuid: "instance-uuid"},
        })

        const {useMiscStore} = await import("override/stores/misc")
        const miscStore = useMiscStore()

        await miscStore.addBasicAuth({username: "admin@kestra.io", password: "StrongPass1"})

        // POST happens before any config is fetched (the endpoint is public/unauthenticated at that point).
        expect(axiosPost).toHaveBeenCalledTimes(1)
        expect(axiosPost.mock.calls[0][0]).toMatch(/\/basicAuth$/)

        // The store now holds the freshly (authenticated) loaded configs.
        expect(axiosGet.mock.calls[0][0]).toMatch(/\/configs$/)
        expect(miscStore.configs).toEqual({isBasicAuthInitialized: true, isUiAnonymousUsageEnabled: true, uuid: "instance-uuid"})

        // Analytics init/event use the freshly loaded configs, not a stale/undefined value.
        expect(initPosthogIfEnabled).toHaveBeenCalledWith(miscStore.configs)
        expect(capturePosthogEvent).toHaveBeenCalledTimes(1)
        const [capturedConfigs, , eventPayload] = capturePosthogEvent.mock.calls[0]
        expect(capturedConfigs).toEqual(miscStore.configs)
        expect(eventPayload.iid).toBe("instance-uuid")
    })

    it("skips posthog init when analytics is disabled, but still fires the ossauth event", async () => {
        axiosGet.mockResolvedValue({
            data: {isBasicAuthInitialized: true, isUiAnonymousUsageEnabled: false, uuid: "instance-uuid-2"},
        })

        const {useMiscStore} = await import("override/stores/misc")
        const miscStore = useMiscStore()

        await miscStore.addBasicAuth({username: "admin2@kestra.io", password: "StrongPass1"})

        expect(initPosthogIfEnabled).not.toHaveBeenCalled()
        expect(disablePosthog).toHaveBeenCalledTimes(1)
        expect(capturePosthogEvent).not.toHaveBeenCalled()
    })
})
