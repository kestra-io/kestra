/**
 * `useAiChat` — client for the AI Copilot v2 agentic loop (`…/ai/threads`).
 *
 * Owns a single thread's lifecycle and reduces the SSE event stream into a
 * renderable message list. The thread `status` is the single source of truth for
 * what the UI may do next:
 *   - IDLE                  → a new turn may be sent
 *   - RUNNING               → a turn is streaming; composer disabled (a 2nd turn 409s)
 *   - AWAITING_CONFIRMATION → a proposal is suspended; call `confirm(...)` to resume
 *
 * Non-streaming calls (create/get) go through the axios client; the `chat` and
 * `confirm` turns are POST SSE streams read via `streamSse`.
 */
import {ref, computed} from "vue"
import {useClient} from "@kestra-io/kestra-sdk"
import {apiUrl} from "override/utils/route"
import {uid} from "../../../utils/utils"
import {streamSse, SseHttpError} from "./streamSse"
import {
    AiEvent,
    type ChatTurnRequest,
    type ConfirmActionRequest,
    type CreateThreadRequest,
    type Decision,
    type DoneEvent,
    type Mode,
    type ProposedActionEvent,
    type ScopeBinding,
    type ThreadDetail,
    type ThreadStatus,
    type ThreadSummary,
    type TokenEvent,
    type ToolCallEvent,
    type ToolResultEvent,
} from "./types"

/** Locale-agnostic error identifier; the component maps it to `ai.copilot.error.<code>`. */
export type ErrorCode = "turnInProgress" | "request" | "generic"

/** A message as rendered in the chat transcript (a superset of the wire MessageView). */
export interface ChatMessage {
    /** Local, stable key for v-for. */
    id: string
    role: "USER" | "ASSISTANT" | "TOOL" | "SYSTEM"
    type: "TEXT" | "TOOL_CALL" | "TOOL_RESULT" | "PROPOSED_ACTION"
    /** Assistant text; appended to as `token` events arrive. */
    content?: string
    toolCall?: ToolCallEvent
    toolResult?: ToolResultEvent
    proposedAction?: ProposedActionEvent
}

export function useAiChat() {
    const client = useClient()

    const thread = ref<ThreadSummary | null>(null)
    const messages = ref<ChatMessage[]>([])
    const status = ref<ThreadStatus>("IDLE")
    const streaming = ref(false)
    /** A translation-key suffix under `ai.copilot.error.*`, or null. Kept locale-agnostic. */
    const error = ref<ErrorCode | null>(null)
    /** The proposal awaiting a confirm/reject decision, if any. */
    const pendingConfirmation = ref<ProposedActionEvent | null>(null)

    /** Reference to the assistant bubble currently being streamed into. */
    let activeAssistant: ChatMessage | null = null
    let abort: AbortController | null = null

    /** True when a new chat turn may be sent. */
    const canSend = computed(() => status.value === "IDLE" && !streaming.value)

    const base = () => `${apiUrl()}/ai/threads`

    /** Creates the thread once and reuses its uid for the rest of the session. */
    async function ensureThread(request: CreateThreadRequest = {}): Promise<ThreadSummary> {
        if (thread.value) return thread.value
        const {data} = await client.post<ThreadSummary>(base(), request)
        thread.value = data
        status.value = data.status
        return data
    }

    /** Rehydrates an existing thread's transcript on reload. Sorts messages by uid. */
    async function loadThread(threadId: string): Promise<void> {
        const {data} = await client.get<ThreadDetail>(`${base()}/${threadId}`)
        thread.value = {
            uid: data.uid,
            title: data.title,
            mode: data.mode,
            scope: data.scope,
            status: data.status,
            createdAt: "",
            updatedAt: "",
        }
        status.value = data.status
        messages.value = [...data.messages]
            .sort((a, b) => a.uid.localeCompare(b.uid))
            .map((m) => ({
                id: m.uid,
                role: m.role,
                type: m.type,
                content: m.content ?? undefined,
                toolCall: m.toolCall
                    ? {tool: m.toolCall.tool, family: m.toolCall.family, arguments: m.toolCall.arguments}
                    : undefined,
                toolResult: (m.toolResult as unknown as ToolResultEvent) ?? undefined,
            }))
    }

    /** Sends a user turn and streams the response. Creates the thread if needed. */
    async function sendChat(request: ChatTurnRequest): Promise<void> {
        if (!canSend.value) return
        const active = await ensureThread({mode: request.mode})

        error.value = null
        pendingConfirmation.value = null
        push({id: uid(), role: "USER", type: "TEXT", content: request.prompt})
        await runStream(`${base()}/${active.uid}/chat`, request)
    }

    /** Resolves a pending proposal. APPROVE resumes & dispatches; REJECT records rejection. */
    async function confirm(decision: Decision, reason?: string): Promise<void> {
        const active = thread.value
        const proposal = pendingConfirmation.value
        if (!active || !proposal) return

        const request: ConfirmActionRequest = {confirmationId: proposal.confirmationId, decision, reason}
        pendingConfirmation.value = null
        await runStream(`${base()}/${active.uid}/confirm`, request)
    }

    /** Cancels an in-flight stream (e.g. on unmount). */
    function cancel(): void {
        abort?.abort()
    }

    /** Shared streaming driver for both chat and confirm turns. */
    async function runStream(url: string, body: unknown): Promise<void> {
        streaming.value = true
        status.value = "RUNNING"
        activeAssistant = null
        abort = new AbortController()

        try {
            await streamSse({url, body, signal: abort.signal, onFrame: reduce})
        } catch (e) {
            if ((e as Error)?.name === "AbortError") return
            error.value = toErrorCode(e)
            // A stream error never leaves us in RUNNING; fall back to a safe resting state.
            status.value = "IDLE"
        } finally {
            streaming.value = false
            activeAssistant = null
            abort = null
        }
    }

    /** Reduces one SSE frame into transcript state. */
    function reduce(frame: {event: string; data: unknown}): void {
        switch (frame.event) {
            case AiEvent.TOKEN: {
                const {text} = frame.data as TokenEvent
                if (!activeAssistant) {
                    push({id: uid(), role: "ASSISTANT", type: "TEXT", content: ""})
                    // Hold the reactive array element (not the raw object) so token
                    // appends trigger re-renders through Vue's proxy.
                    activeAssistant = messages.value[messages.value.length - 1]
                }
                activeAssistant.content = (activeAssistant.content ?? "") + text
                break
            }
            case AiEvent.TOOL_CALL: {
                activeAssistant = null
                push({id: uid(), role: "TOOL", type: "TOOL_CALL", toolCall: frame.data as ToolCallEvent})
                break
            }
            case AiEvent.TOOL_RESULT: {
                push({id: uid(), role: "TOOL", type: "TOOL_RESULT", toolResult: frame.data as ToolResultEvent})
                break
            }
            case AiEvent.PROPOSED_ACTION: {
                activeAssistant = null
                const proposal = frame.data as ProposedActionEvent
                pendingConfirmation.value = proposal
                push({id: uid(), role: "ASSISTANT", type: "PROPOSED_ACTION", proposedAction: proposal})
                break
            }
            case AiEvent.DONE: {
                // The resting state the stream closed into — authoritative.
                status.value = (frame.data as DoneEvent).status
                break
            }
        }
    }

    function push(message: ChatMessage): void {
        messages.value.push(message)
    }

    function toErrorCode(e: unknown): ErrorCode {
        if (e instanceof SseHttpError) {
            return e.status === 409 ? "turnInProgress" : "request"
        }
        return "generic"
    }

    return {
        // State — treat as read-only from consumers (mutated only via the actions below).
        // Returned as plain refs (not readonly()) so they bind cleanly to child component
        // props without DeepReadonly friction.
        thread,
        messages,
        status,
        streaming,
        error,
        pendingConfirmation,
        canSend,
        // actions
        ensureThread,
        loadThread,
        sendChat,
        confirm,
        cancel,
    }
}

export type {Mode, ScopeBinding}
