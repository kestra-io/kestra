import {describe, it, expect, vi, beforeEach} from "vitest"
import type {AiSseFrame} from "../../../../../src/components/ai/copilot/types"

// Mock the axios client (thread create/get) and the SSE reader so we can drive
// frames deterministically without a backend.
const post = vi.fn()
const get = vi.fn()
vi.mock("@kestra-io/kestra-sdk", () => ({useClient: () => ({post, get})}))

let nextFrames: AiSseFrame[] = []
let nextError: Error | null = null
/** Records the JSON body of the most recent stream (chat/confirm) so tests can assert what was sent. */
let lastBody: Record<string, unknown> | null = null
vi.mock("../../../../../src/components/ai/copilot/streamSse", async (importOriginal) => {
    const actual = await importOriginal<typeof import("../../../../../src/components/ai/copilot/streamSse")>()
    return {
        ...actual,
        streamSse: vi.fn(async ({onFrame, body}: {onFrame: (f: AiSseFrame) => void; body: Record<string, unknown>}) => {
            lastBody = body
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
        lastBody = null
        localStorage.clear()
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
        // The resumed turn runs a fresh model call, so confirm must forward the provider it was given.
        await chat.confirm("APPROVE", undefined, "gemini-legacy")
        expect(lastBody).toMatchObject({confirmationId: "c1", decision: "APPROVE", providerId: "gemini-legacy"})
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

    it("surfaces the server reason and resets to IDLE when a turn streams an error event", async () => {
        const chat = useAiChat()
        nextFrames = [{event: "error", data: {message: "LLM streaming call failed: model not found"}}]
        await chat.sendChat({prompt: "hi"})
        expect(chat.errorDetail.value).toBe("LLM streaming call failed: model not found")
        expect(chat.status.value).toBe("IDLE")
        // An error event must not also raise the emptyTurn notice.
        expect(chat.notice.value).toBeNull()
    })

    it("falls back to the request error code when an error event carries no message", async () => {
        const chat = useAiChat()
        nextFrames = [{event: "error", data: {message: ""}}]
        await chat.sendChat({prompt: "hi"})
        expect(chat.errorDetail.value).toBeNull()
        expect(chat.error.value).toBe("request")
    })

    it("clears a prior error detail when a new turn starts", async () => {
        const chat = useAiChat()
        nextFrames = [{event: "error", data: {message: "boom"}}]
        await chat.sendChat({prompt: "hi"})
        expect(chat.errorDetail.value).toBe("boom")
        nextFrames = [{event: "token", data: {text: "ok"}}, {event: "done", data: {status: "IDLE"}}]
        await chat.sendChat({prompt: "again"})
        expect(chat.errorDetail.value).toBeNull()
    })

    it("surfaces the emptyTurn notice when a turn streams only done (no output)", async () => {
        const chat = useAiChat()
        nextFrames = [{event: "done", data: {status: "IDLE"}}]
        await chat.sendChat({prompt: "hi"})
        expect(chat.notice.value).toBe("emptyTurn")
        expect(chat.status.value).toBe("IDLE")
    })

    it("does not set the emptyTurn notice when the turn produced output", async () => {
        const chat = useAiChat()
        nextFrames = [
            {event: "token", data: {text: "Hi there"}},
            {event: "done", data: {status: "IDLE"}},
        ]
        await chat.sendChat({prompt: "hi"})
        expect(chat.notice.value).toBeNull()
    })

    it("does not set the emptyTurn notice when the turn suspends on a proposal", async () => {
        const chat = useAiChat()
        nextFrames = [
            {event: "proposed_action", data: {confirmationId: "c1", tool: "restart-execution", family: "MUTATE", summary: "Restart"}},
            {event: "done", data: {status: "AWAITING_CONFIRMATION"}},
        ]
        await chat.sendChat({prompt: "restart it", mode: "EDIT"})
        expect(chat.notice.value).toBeNull()
    })

    it("retryLastTurn re-runs the last turn and clears the notice when output arrives", async () => {
        const chat = useAiChat()
        nextFrames = [{event: "done", data: {status: "IDLE"}}]
        await chat.sendChat({prompt: "hi", providerId: "gemini-legacy"})
        expect(chat.notice.value).toBe("emptyTurn")

        nextFrames = [
            {event: "token", data: {text: "now answered"}},
            {event: "done", data: {status: "IDLE"}},
        ]
        await chat.retryLastTurn()
        // Same request body was replayed, and the successful turn cleared the notice.
        expect(lastBody).toMatchObject({prompt: "hi", providerId: "gemini-legacy"})
        expect(chat.notice.value).toBeNull()
        expect(chat.messages.value.some((m) => m.role === "ASSISTANT" && m.content === "now answered")).toBe(true)
    })

    it("retryLastTurn is a no-op before any turn has run", async () => {
        const chat = useAiChat()
        await chat.retryLastTurn()
        expect(chat.streaming.value).toBe(false)
        expect(chat.messages.value).toHaveLength(0)
    })

    it("clears a prior emptyTurn notice when a new turn starts", async () => {
        const chat = useAiChat()
        nextFrames = [{event: "done", data: {status: "IDLE"}}]
        await chat.sendChat({prompt: "first"})
        expect(chat.notice.value).toBe("emptyTurn")

        nextFrames = [
            {event: "token", data: {text: "now I answer"}},
            {event: "done", data: {status: "IDLE"}},
        ]
        await chat.sendChat({prompt: "second"})
        expect(chat.notice.value).toBeNull()
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

    it("maps a 429 to the turnCap error code", async () => {
        const chat = useAiChat()
        nextError = new SseHttpError(429, "")
        await chat.sendChat({prompt: "hi"})
        expect(chat.error.value).toBe("turnCap")
        expect(chat.status.value).toBe("IDLE")
    })

    it("remembers the thread uid and restoreThread() rehydrates it on reload", async () => {
        // First session: create a thread (persists its uid to localStorage).
        const first = useAiChat()
        nextFrames = [{event: "done", data: {status: "IDLE"}}]
        await first.sendChat({prompt: "hi"})
        expect(localStorage.getItem("kestra.copilot.activeThread")).toBe("t1")

        // Second session (reload): restoreThread loads the remembered thread's transcript.
        get.mockResolvedValue({data: {
            uid: "t1", mode: "ASK", status: "IDLE",
            messages: [{uid: "a", role: "USER", type: "TEXT", content: "hi"}],
        }})
        const reloaded = useAiChat()
        await reloaded.restoreThread()
        expect(get).toHaveBeenCalledWith("http://localhost/api/v1/main/ai/threads/t1", expect.objectContaining({showMessageOnError: false}))
        expect(reloaded.thread.value?.uid).toBe("t1")
        expect(reloaded.messages.value.some((m) => m.content === "hi")).toBe(true)
    })

    it("restoreThread() forgets a stored thread that no longer exists (404) and stays empty", async () => {
        localStorage.setItem("kestra.copilot.activeThread", "gone")
        get.mockRejectedValue({status: 404})
        const chat = useAiChat()
        await chat.restoreThread()
        expect(chat.thread.value).toBeNull()
        expect(localStorage.getItem("kestra.copilot.activeThread")).toBeNull()
        // The load opts out of the global "page not found" so the expected 404 is handled here,
        // not by redirecting the whole app to the 404 page.
        expect(get).toHaveBeenCalledWith(expect.stringContaining("/gone"), expect.objectContaining({showMessageOnError: false}))
    })

    it("loadThread rethrows a non-404 error (does not silently forget on e.g. a 500)", async () => {
        localStorage.setItem("kestra.copilot.activeThread", "t1")
        get.mockRejectedValue({status: 500})
        const chat = useAiChat()
        await expect(chat.loadThread("t1")).rejects.toBeTruthy()
        // A transient server error must not drop the remembered thread.
        expect(localStorage.getItem("kestra.copilot.activeThread")).toBe("t1")
    })

    it("loadThread reconstructs a pending proposal from pendingConfirmationId + the transcript", async () => {
        get.mockResolvedValue({data: {
            uid: "t2", mode: "EDIT", status: "AWAITING_CONFIRMATION", pendingConfirmationId: "cf-9",
            messages: [
                {uid: "a", role: "USER", type: "TEXT", content: "restart it"},
                {uid: "b", role: "ASSISTANT", type: "PROPOSED_ACTION", content: "Run restart-execution on exec-1",
                 toolCall: {tool: "restart-execution", kind: "PLATFORM", family: "MUTATE", arguments: {id: "exec-1"}}},
            ],
        }})
        const chat = useAiChat()
        await chat.loadThread("t2")
        expect(chat.status.value).toBe("AWAITING_CONFIRMATION")
        expect(chat.pendingConfirmation.value).toMatchObject({
            confirmationId: "cf-9",
            summary: "Run restart-execution on exec-1",
            tool: "restart-execution",
        })
    })

    it("reset() forgets the remembered thread", async () => {
        const chat = useAiChat()
        nextFrames = [{event: "done", data: {status: "IDLE"}}]
        await chat.sendChat({prompt: "hi"})
        expect(localStorage.getItem("kestra.copilot.activeThread")).toBe("t1")
        chat.reset()
        expect(localStorage.getItem("kestra.copilot.activeThread")).toBeNull()
    })
})
