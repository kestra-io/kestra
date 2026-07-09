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
    canSend: ref(true),
    sendChat: vi.fn(),
    confirm: vi.fn(),
    cancel: vi.fn(),
    reset: vi.fn(),
}
vi.mock("../../../../../src/components/ai/copilot/useAiChat", () => ({useAiChat: () => state}))
// The provider list is fetched on mount — stub the SDK so no real request fires.
vi.mock("@kestra-io/kestra-sdk/ai", () => ({providers: vi.fn().mockResolvedValue([])}))

import CopilotChat from "../../../../../src/components/ai/copilot/CopilotChat.vue"

const mountChat = (props = {}) => mount(CopilotChat, {props, global: mountGlobal})

describe("CopilotChat", () => {
    beforeEach(() => {
        state.messages.value = []
        state.error.value = null
        state.pendingConfirmation.value = null
        state.canSend.value = true
        state.streaming.value = false
        state.sendChat.mockReset()
        state.confirm.mockReset()
        state.reset.mockReset()
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
})
