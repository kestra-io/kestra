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
        const indicator = w.find("[data-test=\"copilot-thinking\"]")
        expect(indicator.exists()).toBe(true)
        // Decorative: the rotating flavour words are hidden from the a11y tree (no screen-reader spam).
        expect(indicator.attributes("aria-hidden")).toBe("true")
        expect(w.text()).toContain("Orchestrating")
        // The rising dots are a decorative, aria-hidden pseudo-element host.
        const dots = w.find(".copilot-thinking-dots")
        expect(dots.exists()).toBe(true)
        expect(dots.attributes("aria-hidden")).toBe("true")
    })

    it("rotates to a different (random) word every 5 seconds, never repeating the current one", async () => {
        vi.useFakeTimers()
        // Random sequence: 0 → start on the first word; 0.5 → the next tick picks a different index.
        const seq = [0, 0.5]
        let i = 0
        vi.spyOn(Math, "random").mockImplementation(() => seq[i++] ?? 0)
        const w = mountThinking()
        const first = w.text()

        vi.advanceTimersByTime(5000)
        await w.vm.$nextTick()
        expect(w.text()).not.toBe(first)
    })
})
