import {describe, it, expect, beforeEach, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const get = vi.fn()
vi.mock("@kestra-io/kestra-sdk", () => ({useClient: () => ({get, post: vi.fn()})}))
vi.mock("override/utils/route", () => ({apiUrl: () => "", apiUrlWithoutTenants: () => ""}))
vi.mock("../../../src/stores/api", () => ({useApiStore: () => ({flushQueuedEvents: vi.fn()})}))

import {useMiscStore} from "../../../src/override/stores/misc"
import {aiEnabledGuard, useAiEnabled} from "../../../src/composables/useAiEnabled"

describe("useAiEnabled", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        get.mockReset()
    })

    it("hides the Copilot only once the backend reports it disabled", () => {
        const store = useMiscStore()
        const aiEnabled = useAiEnabled()
        expect(aiEnabled.value).toBe(true)

        store.configs = {isAiEnabled: false} as typeof store.configs
        expect(aiEnabled.value).toBe(false)
    })

    it("redirects away from the Copilot page when AI is disabled", async () => {
        get.mockResolvedValue({data: {isAiEnabled: false}})

        expect(await aiEnabledGuard()).toEqual({name: "home"})
    })

    it("lets the Copilot page load when AI is enabled", async () => {
        useMiscStore().configs = {isAiEnabled: true} as ReturnType<typeof useMiscStore>["configs"]

        expect(await aiEnabledGuard()).toBe(true)
        expect(get).not.toHaveBeenCalled()
    })
})
