import {describe, it, expect, vi, afterEach} from "vitest"
import {mount} from "@vue/test-utils"
import CopilotThinking from "../../../../../src/components/ai/copilot/CopilotThinking.vue"
import {mountGlobal} from "./_helpers"

// Stub the built-in transition so word swaps apply synchronously (no real transitions in jsdom).
const mountThinking = () =>
    mount(CopilotThinking, {global: {...mountGlobal, stubs: {...mountGlobal.stubs, transition: true}}})

describe("CopilotThinking", () => {
    afterEach(() => {
        vi.restoreAllMocks()
        vi.useRealTimers()
    })

    it("renders a rotating orchestration word with an animated dots element", () => {
        vi.spyOn(Math, "random").mockReturnValue(0) // start on the first word
        const w = mountThinking()
        expect(w.find("[data-test=\"copilot-thinking\"]").exists()).toBe(true)
        expect(w.text()).toContain("Orchestrating")
        // The rising dots are a decorative, aria-hidden pseudo-element host.
        const dots = w.find(".copilot-thinking-dots")
        expect(dots.exists()).toBe(true)
        expect(dots.attributes("aria-hidden")).toBe("true")
    })

    it("advances to the next word every 5 seconds", async () => {
        vi.useFakeTimers()
        vi.spyOn(Math, "random").mockReturnValue(0)
        const w = mountThinking()
        expect(w.text()).toContain("Orchestrating")

        vi.advanceTimersByTime(5000)
        await w.vm.$nextTick()
        expect(w.text()).toContain("Scheduling")
    })
})
