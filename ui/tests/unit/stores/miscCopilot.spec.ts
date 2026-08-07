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
        // Seeded by default: the user reviews the prompt and sends it.
        expect(store.copilotPrompt).toBe("Fix this error")
        expect(store.copilotAutoSend).toBe(false)
        expect(store.contextInfoBarOpenTab).toBe("ai")
        expect(store.lastContextTab).toBe("ai")
    })

    // OSS has no thread list to get back to a dropped conversation, so newThread must stay inert here.
    it("promptCopilot stores the thread title but never arms a new thread in OSS", () => {
        const store = useMiscStore()
        store.promptCopilot("Fix this error", {title: "Fix task extract", newThread: true})
        expect(store.copilotThreadTitle).toBe("Fix task extract")
        expect(store.copilotNewThread).toBe(false)
    })

    it("promptCopilot can hand over a prompt to send right away", () => {
        // Single-purpose entry points ("Generate a unit test") start the turn on click, on their own.
        const store = useMiscStore()
        store.promptCopilot("Generate a unit test", {autoSend: true})
        expect(store.copilotPrompt).toBe("Generate a unit test")
        expect(store.copilotAutoSend).toBe(true)
        expect(store.contextInfoBarOpenTab).toBe("ai")
    })
})
