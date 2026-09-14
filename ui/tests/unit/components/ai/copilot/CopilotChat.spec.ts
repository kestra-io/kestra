import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
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
// DiffView (rendered behind a pending MUTATE confirmation) binds useEditorBindings, which pulls in
// three Pinia stores — stub it out, matching VarValue.spec.ts / FlowFileEditorTab.spec.ts.
vi.mock("../../../../../src/composables/useEditorBindings", () => ({useEditorBindings: () => ({})}))
// CopilotChat derives the page scope from the current route — mock a mutable route so tests control it.
// `useRouter` is needed too: a rendered ARTEFACT_DRAFT message mounts the real `CopilotArtefactDraft.vue`,
// which calls `useApplyDraft()`.
let routeStub: {name?: string; params: Record<string, any>} = {name: undefined, params: {}}
vi.mock("vue-router", () => ({useRoute: () => routeStub, useRouter: () => ({push: vi.fn()})}))
// The provider list is fetched on mount — stub the SDK so no real request fires.
vi.mock("@kestra-io/kestra-sdk/ai", () => ({providers: vi.fn().mockResolvedValue([])}))
// CopilotChat reads a seeded prompt and the AI-availability flag from the misc store. Shared
// mutable stub (reactive, so a mid-test `configs` swap re-renders) since there's no Pinia in the
// unit env.
const miscStore = reactive({
    copilotPrompt: null as string | null,
    copilotThreadTitle: null as string | null,
    copilotNewThread: false,
    copilotSendInitialMessage: false,
    configs: {isAiApiKeyConfigured: true} as Record<string, any> | undefined,
    openCopilot: vi.fn(),
    promptCopilot: vi.fn(),
})
vi.mock("override/stores/misc", () => ({useMiscStore: () => miscStore}))
// CopilotChat reads the flow editor's buffer from the flow store so a turn on the flow
// create/edit pages can carry the unsaved source (kestra-io/kestra-ee#10419), and writes
// `previewSource` back so the main editor mirrors a pending confirmation/draft's diff.
const flowStore = reactive({flowYaml: "", previewSource: undefined as string | undefined})
vi.mock("../../../../../src/stores/flow", () => ({useFlowStore: () => flowStore}))

import CopilotChat from "../../../../../src/components/ai/copilot/CopilotChat.vue"
import CopilotThreadControls from "override/components/ai/copilot/CopilotThreadControls.vue"
import {providers as providersMock} from "@kestra-io/kestra-sdk/ai"

// Tracked and unmounted after each test: a live instance keeps watching `miscStore.copilotPrompt`,
// so a leaked one from an earlier test would consume the next test's seeded prompt before its own
// instance mounts.
const mounted: ReturnType<typeof mount>[] = []

const mountChat = (props = {}) => {
    const wrapper = mount(CopilotChat, {props, global: mountGlobal})
    mounted.push(wrapper)
    return wrapper
}

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
        state.cancel.mockReset()
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
        miscStore.copilotSendInitialMessage = false
        miscStore.configs = {isAiApiKeyConfigured: true}
        flowStore.flowYaml = ""
        flowStore.previewSource = undefined
    })

    afterEach(() => {
        mounted.splice(0).forEach((wrapper) => wrapper.unmount())
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
        // Seeded, not sent — the user reviews and submits it.
        expect(state.sendChat).not.toHaveBeenCalled()
    })

    it("sends the seeded prompt itself instead of leaving it in the composer", async () => {
        // "Generate a unit test" and friends: the user already committed by picking the action.
        miscStore.copilotPrompt = "Generate a unit test for the flow hello"
        miscStore.copilotSendInitialMessage = true
        const w = mountChat()
        await flushPromises()
        expect(state.sendChat).toHaveBeenCalledWith(expect.objectContaining({prompt: "Generate a unit test for the flow hello"}))
        const textarea = w.find("[data-test=\"copilot-composer-input\"]").element as HTMLTextAreaElement
        expect(textarea.value).toBe("")
        expect(miscStore.copilotPrompt).toBeNull()
    })

    it("starts a fresh thread before sending the seeded prompt", async () => {
        // "Generate a unit test" must not inherit whatever the restored conversation was about.
        state.thread.value = {uid: "t-1"} as any
        state.messages.value = [{id: "1", role: "USER", type: "TEXT", content: "unrelated"}]
        miscStore.copilotPrompt = "Generate a unit test for the flow hello"
        miscStore.copilotSendInitialMessage = true
        miscStore.copilotNewThread = true
        mountChat()
        await flushPromises()
        expect(state.reset).toHaveBeenCalled()
        expect(state.sendChat).toHaveBeenCalledWith(expect.objectContaining({prompt: "Generate a unit test for the flow hello"}))
        // Reset first, then send — never the other way round.
        expect(state.reset.mock.invocationCallOrder[0]).toBeLessThan(state.sendChat.mock.invocationCallOrder[0])
    })

    it("does not touch the current conversation for a plain seeded prompt", async () => {
        miscStore.copilotPrompt = "Fix this error"
        mountChat()
        await flushPromises()
        expect(state.reset).not.toHaveBeenCalled()
    })

    it("falls back to seeding the prompt while a turn is in flight", async () => {
        // Nothing is dropped: the prompt lands in the composer for the user to send when free.
        state.canSend.value = false
        miscStore.copilotPrompt = "Generate a unit test"
        miscStore.copilotSendInitialMessage = true
        const w = mountChat()
        await flushPromises()
        expect(state.sendChat).not.toHaveBeenCalled()
        const textarea = w.find("[data-test=\"copilot-composer-input\"]").element as HTMLTextAreaElement
        expect(textarea.value).toBe("Generate a unit test")
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

    it("waits for the provider list before sending, so the turn carries a providerId", async () => {
        // The thread restore and the provider fetch are independent calls. Sending as soon as the
        // restore lands would drop providerId and silently take the server default instead.
        // Held open so the restore lands first, the way a slower provider fetch would.
        let resolveProviders: (list: unknown) => void = () => {}
        ;(providersMock as any).mockReturnValueOnce(new Promise((resolve) => { resolveProviders = resolve }))
        miscStore.copilotPrompt = "Generate a unit test"
        miscStore.copilotSendInitialMessage = true
        const w = mountChat()
        await flushPromises()
        expect(state.sendChat).not.toHaveBeenCalled()
        // Visible while the wait lasts: a slow provider fetch must never hide the prompt entirely.
        const textarea = () => w.find("[data-test=\"copilot-composer-input\"]").element as HTMLTextAreaElement
        expect(textarea().value).toBe("Generate a unit test")
        resolveProviders([{id: "gemini", isDefault: true}])
        await flushPromises()
        expect(state.sendChat).toHaveBeenCalledWith(expect.objectContaining({prompt: "Generate a unit test", providerId: "gemini"}))
        // Cleared on the send path, so the sent turn isn't left sitting in the composer too.
        expect(textarea().value).toBe("")
    })

    it("sends on the backend default rather than waiting on a hanging provider fetch", async () => {
        // `/ai/providers` has no client timeout: the turn goes out once the bounded wait elapses.
        vi.useFakeTimers()
        try {
            ;(providersMock as any).mockReturnValueOnce(new Promise(() => {}))
            miscStore.copilotPrompt = "Generate a unit test"
            miscStore.copilotSendInitialMessage = true
            mountChat()
            await vi.advanceTimersByTimeAsync(2000)
            expect(state.sendChat).toHaveBeenCalledWith(expect.objectContaining({prompt: "Generate a unit test", providerId: undefined}))
        } finally {
            vi.useRealTimers()
        }
    })

    it("does not start a turn when the dock closes while the send is still waiting", async () => {
        // Resuming after unmount would stream an SSE turn `onBeforeUnmount(cancel)` already missed.
        let resolveProviders: (list: unknown) => void = () => {}
        ;(providersMock as any).mockReturnValueOnce(new Promise((resolve) => { resolveProviders = resolve }))
        miscStore.copilotPrompt = "Generate a unit test"
        miscStore.copilotSendInitialMessage = true
        const w = mountChat()
        await flushPromises()
        w.unmount()
        resolveProviders([{id: "gemini", isDefault: true}])
        await flushPromises()
        expect(state.sendChat).not.toHaveBeenCalled()
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

    it("passes the open flow's buffer as the diff before-source when the pending action targets it", () => {
        routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
        flowStore.flowYaml = "id: my-flow\nnamespace: company.team"
        state.pendingConfirmation.value = {
            confirmationId: "c1", tool: "update-flow", family: "MUTATE", summary: "Update",
            arguments: {namespace: "company.team", flowId: "my-flow", body: "id: my-flow\nnamespace: company.team\ndescription: x"},
        }
        const w = mountChat()
        expect(w.findComponent({name: "ProposedActionCard"}).props("currentFlowSource")).toBe("id: my-flow\nnamespace: company.team")
    })

    it("omits the diff before-source when the pending action targets a different flow than the one open", () => {
        routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
        flowStore.flowYaml = "id: my-flow\nnamespace: company.team"
        state.pendingConfirmation.value = {
            confirmationId: "c1", tool: "update-flow", family: "MUTATE", summary: "Update",
            arguments: {namespace: "company.team", flowId: "other-flow", body: "id: other-flow"},
        }
        const w = mountChat()
        expect(w.findComponent({name: "ProposedActionCard"}).props("currentFlowSource")).toBeUndefined()
    })

    it("omits the diff before-source outside a flow route", () => {
        routeStub = {name: "flows/list", params: {}}
        state.pendingConfirmation.value = {
            confirmationId: "c1", tool: "update-flow", family: "MUTATE", summary: "Update",
            arguments: {namespace: "company.team", flowId: "my-flow", body: "id: my-flow"},
        }
        const w = mountChat()
        expect(w.findComponent({name: "ProposedActionCard"}).props("currentFlowSource")).toBeUndefined()
    })

    // The main "Flow Code" editor mirrors the same diff live via `flowStore.previewSource`
    // (kestra-io/kestra#19330), so it reads as an in-IDE diff rather than only a chat aside.
    describe("editor diff preview (flowStore.previewSource)", () => {
        const flowDraftMessage = (yaml: string) => ({id: "d1", role: "ASSISTANT", type: "ARTEFACT_DRAFT", draft: {draftId: "d1", kind: "FLOW", yaml, valid: true, constraints: null}})

        it("mirrors the pending mutate confirmation's proposed source when it targets the open flow", () => {
            routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
            flowStore.flowYaml = "id: my-flow\nnamespace: company.team"
            state.pendingConfirmation.value = {
                confirmationId: "c1", tool: "update-flow", family: "MUTATE", summary: "Update",
                arguments: {namespace: "company.team", flowId: "my-flow", body: "id: my-flow\nnamespace: company.team\ndescription: x"},
            }
            mountChat()
            expect(flowStore.previewSource).toBe("id: my-flow\nnamespace: company.team")
        })

        // The real flow-editor URL resolves to the nested `flows/update/edit` route (a flat
        // `flows/update` never occurs in the running app) — regression test for the mirror silently
        // never triggering there (kestra-io/kestra#19330 follow-up).
        it("mirrors the proposed source on the nested /edit tab route actually used by the editor", () => {
            routeStub = {name: "flows/update/edit", params: {namespace: "company.team", id: "my-flow"}}
            flowStore.flowYaml = "id: my-flow\nnamespace: company.team"
            state.pendingConfirmation.value = {
                confirmationId: "c1", tool: "update-flow", family: "MUTATE", summary: "Update",
                arguments: {namespace: "company.team", flowId: "my-flow", body: "id: my-flow\nnamespace: company.team\ndescription: x"},
            }
            mountChat()
            expect(flowStore.previewSource).toBe("id: my-flow\nnamespace: company.team")
        })

        it("mirrors a pending FLOW artefact draft's yaml when it targets the open flow", () => {
            routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
            state.messages.value = [flowDraftMessage("id: my-flow\nnamespace: company.team\ndescription: drafted")]
            mountChat()
            expect(flowStore.previewSource).toBe("id: my-flow\nnamespace: company.team\ndescription: drafted")
        })

        it("does not mirror a draft targeting a different flow than the one open", () => {
            routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
            state.messages.value = [flowDraftMessage("id: other-flow\nnamespace: company.team")]
            mountChat()
            expect(flowStore.previewSource).toBeUndefined()
        })

        it("supersedes an older matching draft with a newer one for the same flow", () => {
            routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
            state.messages.value = [
                flowDraftMessage("id: my-flow\nnamespace: company.team\ndescription: first"),
                flowDraftMessage("id: my-flow\nnamespace: company.team\ndescription: second"),
            ]
            mountChat()
            expect(flowStore.previewSource).toBe("id: my-flow\nnamespace: company.team\ndescription: second")
        })

        it("keeps mirroring an earlier matching draft across an unrelated later draft for a different flow", () => {
            routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
            state.messages.value = [
                flowDraftMessage("id: my-flow\nnamespace: company.team\ndescription: mine"),
                flowDraftMessage("id: other-flow\nnamespace: company.team"),
            ]
            mountChat()
            expect(flowStore.previewSource).toBe("id: my-flow\nnamespace: company.team\ndescription: mine")
        })

        it("clears once the pending mutate confirmation is approved", async () => {
            routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
            flowStore.flowYaml = "id: my-flow\nnamespace: company.team"
            state.pendingConfirmation.value = {
                confirmationId: "c1", tool: "update-flow", family: "MUTATE", summary: "Update",
                arguments: {namespace: "company.team", flowId: "my-flow", body: "id: my-flow\ndescription: x"},
            }
            const w = mountChat()
            expect(flowStore.previewSource).toBeDefined()
            state.pendingConfirmation.value = null // confirm() nulls it on APPROVE/REJECT
            await flushPromises()
            expect(flowStore.previewSource).toBeUndefined()
            void w
        })

        it("clears when a new turn is submitted, even before the draft is superseded", async () => {
            routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
            state.messages.value = [flowDraftMessage("id: my-flow\nnamespace: company.team")]
            const w = mountChat()
            expect(flowStore.previewSource).toBeDefined()
            w.findComponent({name: "CopilotComposer"}).vm.$emit("submit", "revise it")
            await flushPromises()
            expect(flowStore.previewSource).toBeUndefined()
        })

        it("clears when starting a new chat", async () => {
            routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
            state.messages.value = [flowDraftMessage("id: my-flow\nnamespace: company.team")]
            const w = mountChat()
            expect(flowStore.previewSource).toBeDefined()
            await w.find("[data-test=\"copilot-new-chat\"]").trigger("click")
            expect(flowStore.previewSource).toBeUndefined()
            expect(state.reset).toHaveBeenCalled()
        })

        it("clears on unmount", () => {
            routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
            state.messages.value = [flowDraftMessage("id: my-flow\nnamespace: company.team")]
            const w = mountChat()
            expect(flowStore.previewSource).toBeDefined()
            w.unmount()
            expect(flowStore.previewSource).toBeUndefined()
        })

        // Bug 1 (kestra-io/kestra#19330 review): a mirrored preview otherwise locks the editor with no
        // way back to plain editing — dismissing the draft from its transcript card must free it.
        it("clears the preview once its draft is dismissed from the transcript card, and it stays cleared", async () => {
            routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
            state.messages.value = [flowDraftMessage("id: my-flow\nnamespace: company.team")]
            const w = mountChat()
            expect(flowStore.previewSource).toBeDefined()

            const card = w.findComponent({name: "CopilotArtefactDraft"})
            card.vm.$emit("dismiss", "d1")
            await flushPromises()
            expect(flowStore.previewSource).toBeUndefined()

            // A later rescan (anything that changes the message list) must not resurrect it.
            state.messages.value = [...state.messages.value, {id: "u2", role: "USER", type: "TEXT", content: "anything"}]
            await flushPromises()
            expect(flowStore.previewSource).toBeUndefined()
        })

        // Bug 2 (kestra-io/kestra#19330 review): applying a draft clears `previewSource` once (via
        // `flowStore.loadFlow`), but without tracking the draft as applied, the very next rescan of the
        // still-present ARTEFACT_DRAFT message picks it back up as "pending" and re-locks the editor
        // against content that already matches it — an empty diff with no way out.
        it("stays unlocked after a draft is applied, even once something else triggers a rescan", async () => {
            routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
            state.messages.value = [flowDraftMessage("id: my-flow\nnamespace: company.team")]
            const w = mountChat()
            expect(flowStore.previewSource).toBeDefined()

            const card = w.findComponent({name: "CopilotArtefactDraft"})
            card.vm.$emit("applied", "d1")
            await flushPromises()
            expect(flowStore.previewSource).toBeUndefined()

            state.messages.value = [...state.messages.value, {id: "u2", role: "ASSISTANT", type: "TEXT", content: "done"}]
            await flushPromises()
            expect(flowStore.previewSource).toBeUndefined()
        })

        // Round 3 (kestra-io/kestra#19330 review): `dismissedDraftIds`/`appliedDraftIds` are
        // component-local, so they come back empty on a fresh mount (a page reload, or the copilot
        // dock's KeepAlive being destroyed by closing it) — even for a draft that was already applied.
        // A remount is simulated here simply by never emitting "applied" on this instance: the guard
        // must instead recognize the draft is resolved because its YAML already matches the editor.
        it("never locks the editor for a draft whose YAML already matches the editor (e.g. after a remount)", () => {
            routeStub = {name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}
            flowStore.flowYaml = "id: my-flow\nnamespace: company.team"
            state.messages.value = [flowDraftMessage("id: my-flow\nnamespace: company.team")]
            mountChat()
            expect(flowStore.previewSource).toBeUndefined()
        })
    })

    it("disables the composer when a turn cannot be sent", () => {
        state.canSend.value = false
        const w = mountChat()
        expect(w.findComponent({name: "CopilotComposer"}).props("disabled")).toBe(true)
    })

    it("forwards composer stop to cancel while streaming", async () => {
        state.messages.value = [{id: "1", role: "USER", type: "TEXT", content: "hi"}]
        state.streaming.value = true
        state.canSend.value = false
        const w = mountChat()
        expect(w.findComponent({name: "CopilotComposer"}).props("streaming")).toBe(true)
        w.findComponent({name: "CopilotComposer"}).vm.$emit("stop")
        expect(state.cancel).toHaveBeenCalled()
    })

    it("focuses the composer once it re-enables after stop", async () => {
        state.messages.value = [{id: "1", role: "USER", type: "TEXT", content: "hi"}]
        state.streaming.value = true
        state.canSend.value = false
        const w = mount(CopilotChat, {global: mountGlobal, attachTo: document.body})
        try {
            w.findComponent({name: "CopilotComposer"}).vm.$emit("stop")
            await flushPromises()
            const textarea = w.find("[data-test=\"copilot-composer-input\"]").element
            expect(document.activeElement).not.toBe(textarea)

            state.streaming.value = false
            state.canSend.value = true
            await flushPromises()
            expect(document.activeElement).toBe(textarea)
        } finally {
            w.unmount()
        }
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

    it("plays the end gather when a turn finishes", async () => {
        state.messages.value = [
            {id: "1", role: "USER", type: "TEXT", content: "hi"},
            {id: "2", role: "ASSISTANT", type: "TEXT", content: "hello"},
        ]
        state.streaming.value = true
        const w = mountChat()

        state.streaming.value = false
        await flushPromises()

        expect(w.find("[data-test=\"copilot-thinking\"]").exists()).toBe(true)
        expect(w.find(".copilot-mark").classes()).toContain("copilot-mark-end")
    })

    it("does not play the end gather after the user stops the turn", async () => {
        state.messages.value = [
            {id: "1", role: "USER", type: "TEXT", content: "hi"},
            {id: "2", role: "SYSTEM", type: "CANCELLED"},
        ]
        state.streaming.value = true
        const w = mountChat()

        state.streaming.value = false
        await flushPromises()

        expect(w.find("[data-test=\"copilot-thinking\"]").exists()).toBe(false)
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

    // kestra-io/kestra-ee#10739: `/configs` says up front that no provider is configured, but the
    // copilot still opens on the regular chat — the unavailable state waits for a send attempt.
    it("opens on the regular chat when no AI provider is configured", () => {
        miscStore.configs = {isAiApiKeyConfigured: false}
        const w = mountChat()
        expect(w.find("[data-test=\"copilot-unavailable\"]").exists()).toBe(false)
        expect(w.text()).toContain("Turn your idea into a workflow")
        expect(w.findComponent({name: "CopilotComposer"}).exists()).toBe(true)
        expect(w.find(".copilot-suggestions").exists()).toBe(true)
    })

    it("shows the unavailable state once a prompt is attempted with no AI provider configured", async () => {
        miscStore.configs = {isAiApiKeyConfigured: false}
        const w = mountChat()

        // A quick-start suggestion is a send attempt like any other.
        await w.find(".copilot-suggestion").trigger("click")

        expect(w.find("[data-test=\"copilot-unavailable\"]").exists()).toBe(true)
        // The turn is short-circuited: it could only ever come back 503.
        expect(state.sendChat).not.toHaveBeenCalled()
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
