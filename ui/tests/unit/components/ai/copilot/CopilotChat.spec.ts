import {describe, it, expect, vi, beforeEach} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {reactive, ref} from "vue"
import {mountGlobal} from "./_helpers"

// Drive the composable from the test so we can assert how CopilotChat renders each
// state and forwards user intent, without a backend.
const state = {
    thread: ref(null),
    messages: ref<any[]>([]),
    status: ref("IDLE"),
    streaming: ref(false),
    error: ref<string | null>(null),
    notice: ref<string | null>(null),
    pendingConfirmation: ref<any>(null),
    unavailable: ref(false),
    canSend: ref(true),
    nextThreadTitle: ref<string | null>(null),
    sendChat: vi.fn(),
    confirm: vi.fn(),
    cancel: vi.fn(),
    reset: vi.fn(),
    retryLastTurn: vi.fn(),
    loadThread: vi.fn(),
    restoreThread: vi.fn(),
    noteContext: vi.fn(),
}
vi.mock("../../../../../src/components/ai/copilot/useAiChat", () => ({useAiChat: () => state}))
// CopilotChat derives the page scope from the current route — mock a mutable route so tests control it.
let routeStub: {name?: string; params: Record<string, any>} = {name: undefined, params: {}}
vi.mock("vue-router", () => ({useRoute: () => routeStub}))
// The provider list is fetched on mount — stub the SDK so no real request fires.
vi.mock("@kestra-io/kestra-sdk/ai", () => ({providers: vi.fn().mockResolvedValue([])}))
// CopilotChat reads a seeded prompt and the AI-availability flag from the misc store. Shared
// mutable stub (reactive, so a mid-test `configs` swap re-renders) since there's no Pinia in the
// unit env.
const miscStore = reactive({
    copilotPrompt: null as string | null,
    copilotThreadTitle: null as string | null,
    copilotNewThread: false,
    configs: {isAiApiKeyConfigured: true} as Record<string, any> | undefined,
    openCopilot: vi.fn(),
    promptCopilot: vi.fn(),
})
vi.mock("override/stores/misc", () => ({useMiscStore: () => miscStore}))
// CopilotChat reads the flow editor's buffer from the flow store so a turn on the flow
// create/edit pages can carry the unsaved source (kestra-io/kestra-ee#10419).
const flowStore = reactive({flowYaml: ""})
vi.mock("../../../../../src/stores/flow", () => ({useFlowStore: () => flowStore}))

import CopilotChat from "../../../../../src/components/ai/copilot/CopilotChat.vue"
import CopilotThreadControls from "override/components/ai/copilot/CopilotThreadControls.vue"
import {providers as providersMock} from "@kestra-io/kestra-sdk/ai"

const mountChat = (props = {}) => mount(CopilotChat, {props, global: mountGlobal})

describe("CopilotChat", () => {
    beforeEach(() => {
        state.messages.value = []
        state.error.value = null
        state.notice.value = null
        state.pendingConfirmation.value = null
        state.unavailable.value = false
        state.canSend.value = true
        state.streaming.value = false
        routeStub = {name: undefined, params: {}}
        state.sendChat.mockReset()
        state.confirm.mockReset()
        state.reset.mockReset()
        state.retryLastTurn.mockReset()
        state.loadThread.mockReset()
        state.restoreThread.mockReset()
        state.noteContext.mockReset()
        state.thread.value = null
        state.nextThreadTitle.value = null
        miscStore.copilotPrompt = null
        miscStore.copilotThreadTitle = null
        miscStore.copilotNewThread = false
        miscStore.configs = {isAiApiKeyConfigured: true}
        flowStore.flowYaml = ""
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

    it("prefills the composer from a seeded prompt and clears it", async () => {
        miscStore.copilotPrompt = "Fix this error"
        const w = mountChat()
        await flushPromises()
        const textarea = w.find("[data-test=\"copilot-composer-input\"]").element as HTMLTextAreaElement
        expect(textarea.value).toBe("Fix this error")
        // Consumed once, so it doesn't re-seed on the next open.
        expect(miscStore.copilotPrompt).toBeNull()
    })

    // kestra-io/kestra-ee#10424: a seeded fix must not stack onto the active conversation.
    it("drops the active conversation and titles the next thread when the seeded prompt asks for a new thread", async () => {
        state.thread.value = {uid: "t-1"} as any
        state.messages.value = [{id: "1", role: "USER", type: "TEXT", content: "unrelated"}]
        miscStore.copilotPrompt = "Fix the task extract"
        miscStore.copilotThreadTitle = "Fix task extract"
        miscStore.copilotNewThread = true
        mountChat()
        await flushPromises()
        expect(state.reset).toHaveBeenCalled()
        expect(state.nextThreadTitle.value).toBe("Fix task extract")
        // Consumed once, so a later open doesn't reset again.
        expect(miscStore.copilotNewThread).toBe(false)
        expect(miscStore.copilotThreadTitle).toBeNull()
    })

    it("seeds a new-thread fix without resetting when the chat is already fresh", async () => {
        miscStore.copilotPrompt = "Fix the task extract"
        miscStore.copilotThreadTitle = "Fix task extract"
        miscStore.copilotNewThread = true
        mountChat()
        await flushPromises()
        expect(state.reset).not.toHaveBeenCalled()
        expect(state.nextThreadTitle.value).toBe("Fix task extract")
    })

    it("forwards a composer submit to sendChat with the current mode (no scope off a plain route)", async () => {
        const w = mountChat({initialMode: "PLAN"})
        w.findComponent({name: "CopilotComposer"}).vm.$emit("submit", "do it")
        await flushPromises()
        expect(state.sendChat).toHaveBeenCalledWith({prompt: "do it", mode: "PLAN", additionalContext: undefined, providerId: undefined})
    })

    it("sends the current page as additionalContext on a detail route (context-awareness)", async () => {
        routeStub = {name: "executions/update", params: {namespace: "company.team", flowId: "my-flow", id: "exec-1"}}
        const w = mountChat()
        w.findComponent({name: "CopilotComposer"}).vm.$emit("submit", "why did this fail?")
        await flushPromises()
        expect(state.sendChat).toHaveBeenCalledWith(expect.objectContaining({
            prompt: "why did this fail?",
            additionalContext: {currentView: {kind: "EXECUTION", namespace: "company.team", flowId: "my-flow", executionId: "exec-1"}},
        }))
    })

    // kestra-io/kestra-ee#10419: a flow pasted on the create page was never saved, so the agent
    // has nothing to read — the turn must carry the editor buffer itself.
    it("sends the editor buffer as flowSource on the flow create page", async () => {
        routeStub = {name: "flows/create", params: {}}
        flowStore.flowYaml = "id: repro\nnamespace: company.team"
        const w = mountChat()
        w.findComponent({name: "CopilotComposer"}).vm.$emit("submit", "fix this error")
        await flushPromises()
        expect(state.sendChat).toHaveBeenCalledWith(expect.objectContaining({
            additionalContext: {currentView: {kind: "FLOW", flowSource: "id: repro\nnamespace: company.team"}},
        }))
    })

    it("sends the editor buffer alongside the flow ids on a flow detail route", async () => {
        routeStub = {name: "flows/update/edit", params: {namespace: "company.team", id: "my-flow"}}
        flowStore.flowYaml = "id: my-flow"
        const w = mountChat()
        w.findComponent({name: "CopilotComposer"}).vm.$emit("submit", "what does this flow do?")
        await flushPromises()
        expect(state.sendChat).toHaveBeenCalledWith(expect.objectContaining({
            additionalContext: {currentView: {kind: "FLOW", namespace: "company.team", flowId: "my-flow", flowSource: "id: my-flow"}},
        }))
    })

    it("drops the editor buffer when the flow context pill is dismissed", async () => {
        routeStub = {name: "flows/update/edit", params: {namespace: "company.team", id: "my-flow"}}
        flowStore.flowYaml = "id: my-flow"
        const w = mountChat()
        w.findComponent({name: "CopilotContextChip"}).vm.$emit("remove", "flowId")
        await flushPromises()
        w.findComponent({name: "CopilotComposer"}).vm.$emit("submit", "no flow please")
        await flushPromises()
        expect(state.sendChat).toHaveBeenCalledWith(expect.objectContaining({
            additionalContext: {currentView: {kind: "FLOW", namespace: "company.team"}},
        }))
    })

    it("shows the context chip on a detail route and hides it on a plain route", async () => {
        routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
        expect(mountChat().findComponent({name: "CopilotContextChip"}).exists()).toBe(true)

        routeStub = {name: "flows/list", params: {}}
        expect(mountChat().findComponent({name: "CopilotContextChip"}).exists()).toBe(false)
    })

    it("drops each resource from the turn as its context pill is dismissed", async () => {
        routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
        const w = mountChat()
        const chip = w.findComponent({name: "CopilotContextChip"})
        expect(chip.exists()).toBe(true)

        // Dismiss each pill (flow + namespace); the chip disappears once nothing is focused.
        chip.vm.$emit("remove", "flowId")
        chip.vm.$emit("remove", "namespace")
        await flushPromises()
        expect(w.findComponent({name: "CopilotContextChip"}).exists()).toBe(false)
        // Each removal is announced in the transcript (display-only).
        expect(state.noteContext).toHaveBeenCalledWith({action: "removed", noun: "ai.copilot.contextNoun.flow", id: "my-flow"})
        expect(state.noteContext).toHaveBeenCalledWith({action: "removed", noun: "ai.copilot.contextNoun.namespace", id: "company.team"})

        w.findComponent({name: "CopilotComposer"}).vm.$emit("submit", "no scope please")
        await flushPromises()
        expect(state.sendChat).toHaveBeenCalledWith(expect.objectContaining({prompt: "no scope please", additionalContext: undefined}))
    })

    it("surfaces a warning notice when a turn yields no output", () => {
        state.notice.value = "emptyTurn"
        const w = mountChat()
        const alert = w.find("[data-test=\"copilot-notice\"]")
        expect(alert.exists()).toBe(true)
        expect(alert.text()).toContain("The assistant didn't return a response. Please try again.")
    })

    it("restores the last conversation on mount", () => {
        mountChat()
        expect(state.restoreThread).toHaveBeenCalled()
    })

    it("surfaces the turn-cap error with a start-a-new-chat message", () => {
        state.error.value = "turnCap"
        const w = mountChat()
        expect(w.find(".ks-alert").text()).toContain("start a new chat")
    })

    it("switches thread when the thread controls emit select", async () => {
        const w = mountChat()
        w.findComponent(CopilotThreadControls).vm.$emit("select", "t-42")
        await flushPromises()
        expect(state.loadThread).toHaveBeenCalledWith("t-42")
    })

    it("retries the last turn from the empty-turn notice", async () => {
        state.notice.value = "emptyTurn"
        const w = mountChat()
        await w.find("[data-test=\"copilot-notice-retry\"]").trigger("click")
        expect(state.retryLastTurn).toHaveBeenCalled()
    })

    it("renders the proposed-action card and confirms on approve, forwarding the selected provider", async () => {
        // The resumed turn needs the same provider as the chat turn, so approve must pass it through.
        ;(providersMock as any).mockResolvedValueOnce([{id: "gemini-legacy", isDefault: true}])
        state.pendingConfirmation.value = {confirmationId: "c1", tool: "restart-execution", family: "MUTATE", summary: "Restart"}
        const w = mountChat()
        await flushPromises() // let the provider list resolve so selectedProvider is set
        const card = w.findComponent({name: "ProposedActionCard"})
        expect(card.exists()).toBe(true)
        card.vm.$emit("approve")
        await flushPromises()
        expect(state.confirm).toHaveBeenCalledWith("APPROVE", undefined, "gemini-legacy")
    })

    it("rejects via the proposed-action card", async () => {
        state.pendingConfirmation.value = {confirmationId: "c1", tool: null, summary: "Plan"}
        const w = mountChat()
        w.findComponent({name: "ProposedActionCard"}).vm.$emit("reject")
        await flushPromises()
        expect(state.confirm).toHaveBeenCalledWith("REJECT", undefined, undefined)
    })

    it("disables the composer when a turn cannot be sent", () => {
        state.canSend.value = false
        const w = mountChat()
        expect(w.findComponent({name: "CopilotComposer"}).props("disabled")).toBe(true)
    })

    it("shows the thinking movement while streaming before the next output", () => {
        state.messages.value = [{id: "1", role: "USER", type: "TEXT", content: "hi"}]
        state.streaming.value = true
        const w = mountChat()
        expect(w.find("[data-test=\"copilot-thinking\"]").exists()).toBe(true)
        expect(w.find(".copilot-mark").classes()).toContain("copilot-mark-thinking")
    })

    it("switches to the answering movement while assistant text is streaming", () => {
        state.messages.value = [{id: "2", role: "ASSISTANT", type: "TEXT", content: "partial"}]
        state.streaming.value = true
        const w = mountChat()
        expect(w.find("[data-test=\"copilot-thinking\"]").exists()).toBe(true)
        expect(w.find(".copilot-mark").classes()).toContain("copilot-mark-answering")
    })

    it("starts a new chat via the top bar", async () => {
        state.messages.value = [{id: "1", role: "USER", type: "TEXT", content: "hi"}] // something to reset → shown
        const w = mountChat()
        await w.find("[data-test=\"copilot-new-chat\"]").trigger("click")
        expect(state.reset).toHaveBeenCalled()
    })

    it("hides New chat on a fresh, empty chat and shows it once there is something to reset", () => {
        // beforeEach leaves the chat fresh (no messages, no thread) → nothing to reset.
        expect(mountChat().find("[data-test=\"copilot-new-chat\"]").exists()).toBe(false)

        state.messages.value = [{id: "1", role: "USER", type: "TEXT", content: "hi"}]
        expect(mountChat().find("[data-test=\"copilot-new-chat\"]").exists()).toBe(true)
    })

    it("mounts the thread controls (EE-only Recents; a no-op in OSS)", () => {
        expect(mountChat().findComponent(CopilotThreadControls).exists()).toBe(true)
    })

    it("shows the AI-unavailable state (and no composer) when unavailable", () => {
        state.unavailable.value = true
        const w = mountChat()
        expect(w.find("[data-test=\"copilot-unavailable\"]").exists()).toBe(true)
        expect(w.findComponent({name: "CopilotComposer"}).exists()).toBe(false)
    })

    // Configuring a provider is an instance-config change, so the unavailable state points at the
    // docs rather than offering a retry that could never succeed within the session.
    it("offers the configuration docs — not a retry — from the unavailable state", () => {
        state.unavailable.value = true
        const w = mountChat()
        const docs = w.find("[data-test=\"copilot-unavailable-docs\"]")
        expect(docs.attributes("href")).toContain("kestra.io/docs/ai-tools/ai-copilot")
        expect(docs.attributes("target")).toBe("_blank")
        expect(w.find("[data-test=\"copilot-unavailable-retry\"]").exists()).toBe(false)
    })

    // kestra-io/kestra#18322: no provider configured is known from `/configs` before the first turn,
    // so the surface must say so on load instead of offering a chat that can only fail.
    it("shows the unavailable state on load when no AI provider is configured", () => {
        miscStore.configs = {isAiApiKeyConfigured: false}
        const w = mountChat()
        expect(w.find("[data-test=\"copilot-unavailable\"]").exists()).toBe(true)
        expect(w.findComponent({name: "CopilotComposer"}).exists()).toBe(false)
        expect(w.find(".copilot-suggestions").exists()).toBe(false)
    })

    it("keeps the copilot usable when the availability flag is absent (older backend)", () => {
        miscStore.configs = {}
        expect(mountChat().find("[data-test=\"copilot-unavailable\"]").exists()).toBe(false)

        miscStore.configs = undefined
        expect(mountChat().find("[data-test=\"copilot-unavailable\"]").exists()).toBe(false)
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

    // Accessibility: a screen reader must hear streamed output and be told when the surface errors.
    it("exposes the transcript as a polite live log, flagged busy while a turn streams", () => {
        state.messages.value = [{id: "1", role: "ASSISTANT", type: "TEXT", content: "hi"}]
        state.streaming.value = true
        const body = mountChat().find(".copilot-body")
        expect(body.attributes("role")).toBe("log")
        expect(body.attributes("aria-live")).toBe("polite")
        expect(body.attributes("aria-busy")).toBe("true")
    })

    it("marks the transcript not busy once the turn settles", () => {
        state.messages.value = [{id: "1", role: "ASSISTANT", type: "TEXT", content: "hi"}]
        expect(mountChat().find(".copilot-body").attributes("aria-busy")).toBe("false")
    })

    it("announces the error banner assertively and the empty-turn notice politely", () => {
        state.error.value = "turnCap"
        expect(mountChat().find("[data-test=\"copilot-error\"]").attributes("role")).toBe("alert")

        state.error.value = null
        state.notice.value = "emptyTurn"
        expect(mountChat().find("[data-test=\"copilot-notice\"]").attributes("role")).toBe("status")
    })

    it("spins the in-flight tool call while streaming, and stops once its result arrives", async () => {
        state.messages.value = [
            {id: "u1", role: "USER", type: "TEXT", content: "make a flow"},
            {id: "t1", role: "TOOL", type: "TOOL_CALL", toolCall: {tool: "author-flow", family: "AUTHOR", arguments: {}}},
        ]
        state.streaming.value = true
        const w = mountChat()
        await flushPromises()
        // Last message is the tool call and the turn is streaming → the step shows its spinner.
        expect(w.find(".copilot-tool-spinner").exists()).toBe(true)

        // Its result arrives → the tool call is no longer the last message, so the spinner clears.
        state.messages.value = [
            ...state.messages.value,
            {id: "r1", role: "TOOL", type: "TOOL_RESULT", toolResult: {tool: "author-flow", outcome: "ok"}},
        ]
        await flushPromises()
        expect(w.find(".copilot-tool-spinner").exists()).toBe(false)
    })
})
