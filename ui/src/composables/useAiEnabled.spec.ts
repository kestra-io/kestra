import {describe, it, expect, beforeEach, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const configuration = vi.hoisted(() => vi.fn())
vi.mock("@kestra-io/kestra-sdk/misc", () => ({configuration}))

import {useMiscStore} from "override/stores/misc"
import {aiEnabledGuard, useAiEnabled} from "./useAiEnabled"

describe("useAiEnabled", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        configuration.mockReset()
    })

    it("hides the Copilot only once the backend reports it disabled", () => {
        const store = useMiscStore()
        const aiEnabled = useAiEnabled()
        expect(aiEnabled.value).toBe(true)

        store.configs = {isAiEnabled: false} as typeof store.configs
        expect(aiEnabled.value).toBe(false)
    })

    it("redirects away from the Copilot page when AI is disabled", async () => {
        configuration.mockResolvedValue({isAiEnabled: false})

        expect(await aiEnabledGuard()).toEqual({name: "home"})
    })

    it("lets the Copilot page load when AI is enabled", async () => {
        useMiscStore().configs = {isAiEnabled: true} as ReturnType<typeof useMiscStore>["configs"]

        expect(await aiEnabledGuard()).toBe(true)
        expect(configuration).not.toHaveBeenCalled()
    })
})
