import {describe, it, expect, beforeEach, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

import {useMiscStore} from "override/stores/misc"
import {aiEnabledGuard, useAiEnabled} from "./useAiEnabled"

describe("useAiEnabled", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it("hides the Copilot only once the backend reports it disabled", () => {
        const store = useMiscStore()
        const aiEnabled = useAiEnabled()
        expect(aiEnabled.value).toBe(true)

        store.configs = {isAiEnabled: false} as typeof store.configs
        expect(aiEnabled.value).toBe(false)
    })

    it("redirects away from the Copilot page when AI is disabled", async () => {
        vi.spyOn(useMiscStore(), "loadConfigs").mockResolvedValue({isAiEnabled: false})

        expect(await aiEnabledGuard()).toEqual({name: "home"})
    })

    it("lets the Copilot page load when AI is enabled", async () => {
        const store = useMiscStore()
        store.configs = {isAiEnabled: true} as typeof store.configs
        const loadConfigs = vi.spyOn(store, "loadConfigs")

        expect(await aiEnabledGuard()).toBe(true)
        expect(loadConfigs).not.toHaveBeenCalled()
    })
})
