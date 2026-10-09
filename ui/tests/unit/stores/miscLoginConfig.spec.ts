import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

const {configuration, loginConfiguration} = vi.hoisted(() => ({configuration: vi.fn(), loginConfiguration: vi.fn()}))

vi.mock("@kestra-io/kestra-sdk/misc", () => ({configuration, loginConfiguration}))

vi.mock("../../../src/utils/posthog", () => ({
    initPosthogIfEnabled: vi.fn(),
}))

vi.mock("../../../src/utils/uid", () => ({
    ensureUid: vi.fn(() => "uid-123"),
    getUid: vi.fn(() => "uid-123"),
}))

describe("misc store loadLoginConfig", () => {
    beforeEach(() => {
        vi.resetModules()
        configuration.mockReset()
        loginConfiguration.mockReset()
        setActivePinia(createPinia())
    })

    it("fetches the public /configs/login endpoint and returns its payload without touching the full configs state", async () => {
        loginConfiguration.mockResolvedValue({isBasicAuthInitialized: true})

        const {useMiscStore} = await import("override/stores/misc")
        const miscStore = useMiscStore()

        const result = await miscStore.loadLoginConfig()

        expect(loginConfiguration).toHaveBeenCalledTimes(1)
        expect(configuration).not.toHaveBeenCalled()
        expect(result).toEqual({isBasicAuthInitialized: true})
        // The minimal login-config call must not populate the full configs state
        // (that would defeat the point of keeping the full payload behind auth).
        expect(miscStore.configs).toBeUndefined()
    })

    it("keeps loadConfigs hitting the full /configs endpoint separately", async () => {
        configuration.mockResolvedValue({isBasicAuthInitialized: true, version: "1.2.3"})

        const {useMiscStore} = await import("override/stores/misc")
        const miscStore = useMiscStore()

        await miscStore.loadConfigs()

        expect(loginConfiguration).not.toHaveBeenCalled()
        expect(miscStore.configs).toEqual({isBasicAuthInitialized: true, version: "1.2.3"})
    })
})
