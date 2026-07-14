import {describe, it, expect, beforeEach, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

// The store instantiates the SDK client + reads route helpers at setup; stub both.
vi.mock("@kestra-io/kestra-sdk", () => ({useClient: () => ({get: vi.fn(), post: vi.fn()})}))
vi.mock("override/utils/route", () => ({apiUrl: () => "", apiUrlWithoutTenants: () => ""}))

import {useMiscStore} from "../../../src/override/stores/misc"

describe("misc store — AI Copilot entry points", () => {
    beforeEach(() => setActivePinia(createPinia()))

    it("openCopilot opens the AI context-dock tab", () => {
        const store = useMiscStore()
        store.contextInfoBarOpenTab = ""
        store.openCopilot()
        expect(store.contextInfoBarOpenTab).toBe("ai")
        expect(store.lastContextTab).toBe("ai")
    })

    it("promptCopilot seeds a prompt and opens the tab", () => {
        const store = useMiscStore()
        store.promptCopilot("Fix this error")
        expect(store.copilotPrompt).toBe("Fix this error")
        expect(store.contextInfoBarOpenTab).toBe("ai")
        expect(store.lastContextTab).toBe("ai")
    })
})
