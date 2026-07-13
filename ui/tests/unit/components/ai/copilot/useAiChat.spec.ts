import {describe, it, expect, vi, beforeEach} from "vitest"
import type {AiSseFrame} from "../../../../../src/components/ai/copilot/types"

// Mock the axios client (thread create/get) and the SSE reader so we can drive
// frames deterministically without a backend.
const post = vi.fn()
const get = vi.fn()
vi.mock("@kestra-io/kestra-sdk", () => ({useClient: () => ({post, get})}))

let nextFrames: AiSseFrame[] = []
let nextError: Error | null = null
vi.mock("../../../../../src/components/ai/copilot/streamSse", async (importOriginal) => {
    const actual = await importOriginal<typeof import("../../../../../src/components/ai/copilot/streamSse")>()
    return {
        ...actual,
        streamSse: vi.fn(async ({onFrame}: {onFrame: (f: AiSseFrame) => void}) => {
            if (nextError) throw nextError
            for (const f of nextFrames) onFrame(f)
        }),
    }
})

vi.mock("override/utils/route", () => ({apiUrl: () => "http://localhost/api/v1/main"}))

import {useAiChat} from "../../../../../src/components/ai/copilot/useAiChat"
import {SseHttpError} from "../../../../../src/components/ai/copilot/streamSse"

function idleThread() {
    return {data: {uid: "t1", mode: "ASK", status: "IDLE", createdAt: "", updatedAt: ""}}
}

describe("useAiChat", () => {
    beforeEach(() => {
        post.mockReset()
        get.mockReset()
        nextFrames = []
        nextError = null
        post.mockResolvedValue(idleThread())
    })

    it("creates the thread once and reuses its uid", async () => {
        const chat = useAiChat()
        nextFrames = [{event: "done", data: {status: "IDLE"}}]
        await chat.sendChat({prompt: "hi"})
        await chat.sendChat({prompt: "again"})
        // One create call total, despite two turns.
        expect(post).toHaveBeenCalledTimes(1)
        expect(post.mock.calls[0][0]).toContain("/ai/threads")
    })

    it("appends streamed tokens into a single assistant message", async () => {
        const chat = useAiChat()
        nextFrames = [
            {event: "token", data: {text: "Hel"}},
            {event: "token", data: {text: "lo"}},
            {event: "done", data: {status: "IDLE"}},
        ]
        await chat.sendChat({prompt: "hi"})
        const assistant = chat.messages.value.filter((m) => m.role === "ASSISTANT")
        expect(assistant).toHaveLength(1)
        expect(assistant[0].content).toBe("Hello")
        expect(chat.status.value).toBe("IDLE")
    })

    it("suspends on a proposed_action and exposes it for confirmation", async () => {
        const chat = useAiChat()
        nextFrames = [
            {event: "proposed_action", data: {confirmationId: "c1", tool: "restart-execution", family: "MUTATE", summary: "Restart"}},
            {event: "done", data: {status: "AWAITING_CONFIRMATION"}},
        ]
        await chat.sendChat({prompt: "restart it", mode: "EDIT"})
        expect(chat.status.value).toBe("AWAITING_CONFIRMATION")
        expect(chat.pendingConfirmation.value?.confirmationId).toBe("c1")
        expect(chat.canSend.value).toBe(false)
    })

    it("resolves a pending proposal via confirm and records the tool result", async () => {
        const chat = useAiChat()
        nextFrames = [
            {event: "proposed_action", data: {confirmationId: "c1", tool: "restart-execution", family: "MUTATE", summary: "Restart"}},
            {event: "done", data: {status: "AWAITING_CONFIRMATION"}},
        ]
        await chat.sendChat({prompt: "restart it", mode: "EDIT"})

        nextFrames = [
            {event: "tool_result", data: {tool: "restart-execution", outcome: "ok"}},
            {event: "done", data: {status: "IDLE"}},
        ]
        await chat.confirm("APPROVE")
        expect(chat.pendingConfirmation.value).toBeNull()
        expect(chat.status.value).toBe("IDLE")
        expect(chat.messages.value.some((m) => m.type === "TOOL_RESULT" && m.toolResult?.outcome === "ok")).toBe(true)
    })

    it("does not send a second turn while not IDLE", async () => {
        const chat = useAiChat()
        nextFrames = [{event: "done", data: {status: "AWAITING_CONFIRMATION"}}]
        await chat.sendChat({prompt: "one"})
        const userCount = () => chat.messages.value.filter((m) => m.role === "USER").length
        expect(userCount()).toBe(1)
        await chat.sendChat({prompt: "two"}) // blocked by canSend
        expect(userCount()).toBe(1)
    })

    it("renders a tool_call then its tool_result during an auto-run", async () => {
        const chat = useAiChat()
        nextFrames = [
            {event: "tool_call", data: {tool: "read-execution", family: "READ", arguments: {id: "e1"}}},
            {event: "tool_result", data: {tool: "read-execution", outcome: "ok"}},
            {event: "token", data: {text: "Here you go."}},
            {event: "done", data: {status: "IDLE"}},
        ]
        await chat.sendChat({prompt: "read it"})
        const types = chat.messages.value.map((m) => m.type)
        expect(types).toContain("TOOL_CALL")
        expect(types).toContain("TOOL_RESULT")
        expect(chat.messages.value.find((m) => m.type === "TOOL_CALL")?.toolCall?.tool).toBe("read-execution")
    })

    it("renders an artefact_draft as an inline ARTEFACT_DRAFT message", async () => {
        const chat = useAiChat()
        nextFrames = [
            {event: "token", data: {text: "Here's a draft."}},
            {event: "artefact_draft", data: {draftId: "d1", kind: "FLOW", yaml: "id: demo", valid: true, constraints: null}},
            {event: "done", data: {status: "IDLE"}},
        ]
        await chat.sendChat({prompt: "make a flow", mode: "EDIT"})
        const draftMsg = chat.messages.value.find((m) => m.type === "ARTEFACT_DRAFT")
        expect(draftMsg?.draft?.draftId).toBe("d1")
        expect(draftMsg?.draft?.kind).toBe("FLOW")
        expect(draftMsg?.draft?.yaml).toBe("id: demo")
        // The draft ends the current assistant bubble: a subsequent token starts a new one.
        expect(chat.messages.value.filter((m) => m.type === "TEXT" && m.role === "ASSISTANT")).toHaveLength(1)
    })

    it("rehydrates a persisted artefact draft from thread history", async () => {
        const chat = useAiChat()
        get.mockResolvedValue({data: {
            uid: "t7", mode: "EDIT", status: "IDLE",
            messages: [
                {uid: "a", role: "ASSISTANT", type: "ARTEFACT_DRAFT", draft: {draftId: "d9", kind: "DASHBOARD", yaml: "x: 1", valid: false, constraints: "bad"}},
            ],
        }})
        await chat.loadThread("t7")
        const draftMsg = chat.messages.value.find((m) => m.type === "ARTEFACT_DRAFT")
        expect(draftMsg?.draft?.draftId).toBe("d9")
        expect(draftMsg?.draft?.valid).toBe(false)
    })

    it("maps a 409 to the turnInProgress error code and falls back to IDLE", async () => {
        const chat = useAiChat()
        nextError = new SseHttpError(409, "")
        await chat.sendChat({prompt: "hi"})
        expect(chat.error.value).toBe("turnInProgress")
        expect(chat.status.value).toBe("IDLE")
    })

    it("maps a non-HTTP failure to the generic error code", async () => {
        const chat = useAiChat()
        nextError = new Error("network down")
        await chat.sendChat({prompt: "hi"})
        expect(chat.error.value).toBe("generic")
    })

    it("rehydrates a thread transcript sorted by uid", async () => {
        const chat = useAiChat()
        get.mockResolvedValue({data: {
            uid: "t9", mode: "ASK", status: "IDLE",
            messages: [
                {uid: "b", role: "ASSISTANT", type: "TEXT", content: "second"},
                {uid: "a", role: "USER", type: "TEXT", content: "first"},
            ],
        }})
        await chat.loadThread("t9")
        expect(chat.messages.value.map((m) => m.content)).toEqual(["first", "second"])
        expect(chat.status.value).toBe("IDLE")
    })

    it("resumes a suspended thread by restoring its pending confirmation", async () => {
        const chat = useAiChat()
        get.mockResolvedValue({data: {
            uid: "t5", mode: "PLAN", status: "AWAITING_CONFIRMATION",
            messages: [{uid: "a", role: "USER", type: "TEXT", content: "restart it"}],
            pendingConfirmation: {confirmationId: "c9", tool: "restart-execution", family: "MUTATE", summary: "Restart execution"},
        }})
        await chat.loadThread("t5")
        expect(chat.status.value).toBe("AWAITING_CONFIRMATION")
        expect(chat.pendingConfirmation.value?.confirmationId).toBe("c9")
        expect(chat.canSend.value).toBe(false) // composer stays gated until confirmed
    })

    it("leaves no pending confirmation when resuming an idle thread", async () => {
        const chat = useAiChat()
        get.mockResolvedValue({data: {uid: "t6", mode: "ASK", status: "IDLE", messages: []}})
        await chat.loadThread("t6")
        expect(chat.pendingConfirmation.value).toBeNull()
    })

    it("surfaces the unavailable state when thread creation 503s (no provider)", async () => {
        const chat = useAiChat()
        post.mockRejectedValueOnce({response: {status: 503}})
        await chat.sendChat({prompt: "hi"})
        expect(chat.unavailable.value).toBe(true)
        expect(chat.error.value).toBeNull()
        // No user message is recorded when the turn can't even start.
        expect(chat.messages.value).toHaveLength(0)
    })

    it("surfaces the unavailable state on a 503 mid-stream", async () => {
        const chat = useAiChat()
        nextError = new SseHttpError(503, "")
        await chat.sendChat({prompt: "hi"})
        expect(chat.unavailable.value).toBe(true)
        expect(chat.status.value).toBe("IDLE")
    })

    it("retry() clears the unavailable state", async () => {
        const chat = useAiChat()
        post.mockRejectedValueOnce({response: {status: 503}})
        await chat.sendChat({prompt: "hi"})
        expect(chat.unavailable.value).toBe(true)
        chat.retry()
        expect(chat.unavailable.value).toBe(false)
    })

    it("reset() clears the transcript and thread back to the empty state", async () => {
        const chat = useAiChat()
        nextFrames = [{event: "token", data: {text: "hi"}}, {event: "done", data: {status: "IDLE"}}]
        await chat.sendChat({prompt: "hello"})
        expect(chat.messages.value.length).toBeGreaterThan(0)
        expect(chat.thread.value).not.toBeNull()

        chat.reset()
        expect(chat.messages.value).toEqual([])
        expect(chat.thread.value).toBeNull()
        expect(chat.status.value).toBe("IDLE")
        expect(chat.pendingConfirmation.value).toBeNull()
    })
})
