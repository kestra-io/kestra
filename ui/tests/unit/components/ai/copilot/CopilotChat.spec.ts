import {describe, it, expect, vi, beforeEach} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {ref} from "vue"
import {mountGlobal} from "./_helpers"

// Drive the composable from the test so we can assert how CopilotChat renders each
// state and forwards user intent, without a backend.
const state = {
    thread: ref(null),
    messages: ref<any[]>([]),
    status: ref("IDLE"),
    streaming: ref(false),
    error: ref<string | null>(null),
    pendingConfirmation: ref<any>(null),
    unavailable: ref(false),
    canSend: ref(true),
    sendChat: vi.fn(),
    confirm: vi.fn(),
    cancel: vi.fn(),
    reset: vi.fn(),
    retry: vi.fn(),
}
vi.mock("../../../../../src/components/ai/copilot/useAiChat", () => ({useAiChat: () => state}))
// The provider list is fetched on mount — stub the SDK so no real request fires.
vi.mock("@kestra-io/kestra-sdk/ai", () => ({providers: vi.fn().mockResolvedValue([])}))
// CopilotChat reads a seeded prompt from the misc store on mount; stub it (no Pinia in unit env).
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({copilotPrompt: null, openCopilot: vi.fn(), promptCopilot: vi.fn()})}))

import CopilotChat from "../../../../../src/components/ai/copilot/CopilotChat.vue"

const mountChat = (props = {}) => mount(CopilotChat, {props, global: mountGlobal})

describe("CopilotChat", () => {
    beforeEach(() => {
        state.messages.value = []
        state.error.value = null
        state.pendingConfirmation.value = null
        state.unavailable.value = false
        state.canSend.value = true
        state.streaming.value = false
        state.sendChat.mockReset()
        state.confirm.mockReset()
        state.reset.mockReset()
        state.retry.mockReset()
    })

    it("shows the empty state when there are no messages", () => {
        expect(mountChat().text()).toContain("Turn your idea into a workflow")
    })

    it("renders one message per transcript entry (and hides the empty state)", () => {
        state.messages.value = [
            {id: "1", role: "USER", type: "TEXT", content: "hi"},
            {id: "2", role: "ASSISTANT", type: "TEXT", content: "hello"},
        ]
        const w = mountChat()
        expect(w.text()).not.toContain("Turn your idea into a workflow")
        expect(w.findAllComponents({name: "CopilotMessage"})).toHaveLength(2)
    })

    it("surfaces a translated error alert from the error code", () => {
        state.error.value = "turnInProgress"
        const w = mountChat()
        const alert = w.find(".ks-alert")
        expect(alert.exists()).toBe(true)
        expect(alert.text()).toBe("A turn is already in progress.")
    })

    it("forwards a composer submit to sendChat with the current mode", async () => {
        const w = mountChat({initialMode: "PLAN"})
        w.findComponent({name: "CopilotComposer"}).vm.$emit("submit", "do it")
        await flushPromises()
        expect(state.sendChat).toHaveBeenCalledWith({prompt: "do it", mode: "PLAN", inFocus: undefined})
    })

    it("renders the proposed-action card and confirms on approve", async () => {
        state.pendingConfirmation.value = {confirmationId: "c1", tool: "restart-execution", family: "MUTATE", summary: "Restart"}
        const w = mountChat()
        const card = w.findComponent({name: "ProposedActionCard"})
        expect(card.exists()).toBe(true)
        card.vm.$emit("approve")
        await flushPromises()
        expect(state.confirm).toHaveBeenCalledWith("APPROVE")
    })

    it("rejects via the proposed-action card", async () => {
        state.pendingConfirmation.value = {confirmationId: "c1", tool: null, summary: "Plan"}
        const w = mountChat()
        w.findComponent({name: "ProposedActionCard"}).vm.$emit("reject")
        await flushPromises()
        expect(state.confirm).toHaveBeenCalledWith("REJECT")
    })

    it("disables the composer when a turn cannot be sent", () => {
        state.canSend.value = false
        const w = mountChat()
        expect(w.findComponent({name: "CopilotComposer"}).props("disabled")).toBe(true)
    })

    it("shows the thinking indicator while streaming before the next output", () => {
        state.messages.value = [{id: "1", role: "USER", type: "TEXT", content: "hi"}]
        state.streaming.value = true
        expect(mountChat().find("[data-test=\"copilot-thinking\"]").exists()).toBe(true)
    })

    it("hides the thinking indicator while assistant text is streaming", () => {
        state.messages.value = [{id: "2", role: "ASSISTANT", type: "TEXT", content: "partial"}]
        state.streaming.value = true
        expect(mountChat().find("[data-test=\"copilot-thinking\"]").exists()).toBe(false)
    })

    it("starts a new chat via the top bar", async () => {
        const w = mountChat()
        await w.find("[data-test=\"copilot-new-chat\"]").trigger("click")
        expect(state.reset).toHaveBeenCalled()
    })

    it("shows the recents placeholder control", () => {
        expect(mountChat().find("[data-test=\"copilot-recents\"]").exists()).toBe(true)
    })

    it("shows the AI-unavailable state (and no composer) when unavailable", () => {
        state.unavailable.value = true
        const w = mountChat()
        expect(w.find("[data-test=\"copilot-unavailable\"]").exists()).toBe(true)
        expect(w.findComponent({name: "CopilotComposer"}).exists()).toBe(false)
    })

    it("retries from the unavailable state", async () => {
        state.unavailable.value = true
        const w = mountChat()
        await w.find("[data-test=\"copilot-unavailable-retry\"]").trigger("click")
        expect(state.retry).toHaveBeenCalled()
    })

    it("auto-scrolls the transcript to the bottom as new content arrives", async () => {
        // jsdom doesn't implement scrollIntoView — define it so we can assert it's called.
        const spy = vi.fn()
        ;(HTMLElement.prototype as unknown as {scrollIntoView: unknown}).scrollIntoView = spy
        mountChat()
        state.messages.value = [{id: "1", role: "USER", type: "TEXT", content: "hi"}]
        await flushPromises()
        expect(spy).toHaveBeenCalled()
        delete (HTMLElement.prototype as unknown as {scrollIntoView?: unknown}).scrollIntoView
    })
})
