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
    type ArtefactDraftEvent,
    type ChatTurnRequest,
    type ConfirmActionRequest,
    type CreateThreadRequest,
    type Decision,
    type DoneEvent,
    type ErrorEvent,
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
export type ErrorCode = "turnInProgress" | "turnCap" | "request" | "generic"

/** Locale-agnostic non-error notice identifier; the component maps it to `ai.copilot.notice.<code>`. */
export type NoticeCode = "emptyTurn"

/** A message as rendered in the chat transcript (a superset of the wire MessageView). */
export interface ChatMessage {
    /** Local, stable key for v-for. */
    id: string
    role: "USER" | "ASSISTANT" | "TOOL" | "SYSTEM"
    type: "TEXT" | "TOOL_CALL" | "TOOL_RESULT" | "PROPOSED_ACTION" | "ARTEFACT_DRAFT" | "CANCELLED"
    /** Assistant text; appended to as `token` events arrive. */
    content?: string
    toolCall?: ToolCallEvent
    toolResult?: ToolResultEvent
    proposedAction?: ProposedActionEvent
    /** The drafted artefact, on ARTEFACT_DRAFT messages. */
    draft?: ArtefactDraftEvent
}

export function useAiChat() {
    const client = useClient()

    const thread = ref<ThreadSummary | null>(null)
    const messages = ref<ChatMessage[]>([])
    const status = ref<ThreadStatus>("IDLE")
    const streaming = ref(false)
    /** A translation-key suffix under `ai.copilot.error.*`, or null. Kept locale-agnostic. */
    const error = ref<ErrorCode | null>(null)
    /**
     * The server-supplied failure reason from an SSE `error` event (e.g. "LLM streaming call failed:
     * model not found"). Shown verbatim in preference to the generic `error` code when present.
     */
    const errorDetail = ref<string | null>(null)
    /** A non-error notice (e.g. a turn that produced no output) under `ai.copilot.notice.*`, or null. */
    const notice = ref<NoticeCode | null>(null)
    /** The proposal awaiting a confirm/reject decision, if any. */
    const pendingConfirmation = ref<ProposedActionEvent | null>(null)
    /** True when the backend reports no AI provider is configured (503) — render an "unavailable" state. */
    const unavailable = ref(false)

    /** Reference to the assistant bubble currently being streamed into. */
    let activeAssistant: ChatMessage | null = null
    let abort: AbortController | null = null
    /** The last chat/confirm request, so an empty/failed turn can be retried without retyping. */
    let lastTurn: {url: string; body: unknown} | null = null

    /** True when a new chat turn may be sent. */
    const canSend = computed(() => status.value === "IDLE" && !streaming.value)

    const base = () => `${apiUrl()}/ai/threads`

    // The active thread uid is remembered client-side so the conversation survives a reload
    // (threads are persisted + user-scoped server-side). A stale/foreign uid just 404s → cleared.
    const THREAD_STORAGE_KEY = "kestra.copilot.activeThread"
    const rememberThread = (uid: string) => {
        try {
            localStorage.setItem(THREAD_STORAGE_KEY, uid)
        } catch { /* storage unavailable (private mode) — resume simply won't persist */ }
    }
    const forgetThread = () => {
        try {
            localStorage.removeItem(THREAD_STORAGE_KEY)
        } catch { /* ignore */ }
    }

    /** Creates the thread once and reuses its uid for the rest of the session. */
    async function ensureThread(request: CreateThreadRequest = {}): Promise<ThreadSummary> {
        if (thread.value) return thread.value
        // `showMessageOnError: false` opts out of the global "page not found" redirect: when no AI
        // provider is configured the agentic endpoints (`AiAgentController`, `@Requires` the
        // AiServiceManager bean) aren't registered, so this create 404s — surfaced as the copilot's
        // own "unavailable" state by sendChat, not a full-page redirect.
        const {data} = await client.post<ThreadSummary>(base(), request, {showMessageOnError: false} as any)
        thread.value = data
        status.value = data.status
        rememberThread(data.uid)
        return data
    }

    /**
     * Restores the last conversation on mount from the remembered uid. A cleared / other-user /
     * other-tenant thread 404s → we forget it and start empty. No-op if a thread is already active.
     */
    async function restoreThread(): Promise<void> {
        if (thread.value) return
        let storedId: string | null = null
        try {
            storedId = localStorage.getItem(THREAD_STORAGE_KEY)
        } catch { /* storage unavailable */ }
        if (!storedId) return
        try {
            await loadThread(storedId)
        } catch {
            forgetThread()
        }
    }

    /** Rehydrates an existing thread's transcript on reload. Sorts messages by uid. */
    async function loadThread(threadId: string): Promise<void> {
        // `showMessageOnError: false` opts out of the global "page not found" so an expected 404 —
        // the thread no longer exists (e.g. an evicted OSS in-memory conversation, or a deleted one) —
        // is handled here: forget the remembered id and start a fresh session instead of erroring out.
        const response = await client
            .get<ThreadDetail>(`${base()}/${threadId}`, {showMessageOnError: false} as any)
            .catch((e: {status?: number; response?: {status?: number}}) => {
                if (e?.status === 404 || e?.response?.status === 404) return null
                throw e
            })
        if (!response) {
            forgetThread()
            return
        }
        const {data} = response
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
        error.value = null
        errorDetail.value = null
        notice.value = null
        activeAssistant = null
        messages.value = [...data.messages]
            .sort((a, b) => a.uid.localeCompare(b.uid))
            .map((m) => ({
                id: m.uid,
                role: m.role,
                type: m.type,
                content: m.content ?? undefined,
                toolCall: m.toolCall
                    ? {tool: m.toolCall.tool, kind: m.toolCall.kind, family: m.toolCall.family, arguments: m.toolCall.arguments}
                    : undefined,
                toolResult: (m.toolResult as unknown as ToolResultEvent) ?? undefined,
                draft: m.draft ?? undefined,
            }))

        // Resume a suspended turn: reconstruct the pending proposal so its confirm card renders again.
        // The backend gives only `pendingConfirmationId`; the summary/held-tool come from the last
        // PROPOSED_ACTION message in the transcript.
        pendingConfirmation.value = data.status === "AWAITING_CONFIRMATION" && data.pendingConfirmationId
            ? proposalFromTranscript(data.pendingConfirmationId)
            : null

        rememberThread(data.uid)
    }

    /** Rebuild a ProposedActionEvent from the persisted PROPOSED_ACTION message + the pending id. */
    function proposalFromTranscript(confirmationId: string): ProposedActionEvent {
        const last = [...messages.value].reverse().find((m) => m.type === "PROPOSED_ACTION")
        return {
            confirmationId,
            summary: last?.content ?? "",
            tool: last?.toolCall?.tool ?? null,
            family: last?.toolCall?.family ?? null,
            arguments: last?.toolCall?.arguments ?? null,
        }
    }

    /** Sends a user turn and streams the response. Creates the thread if needed. */
    async function sendChat(request: ChatTurnRequest): Promise<void> {
        if (!canSend.value) return

        // Thread creation can fail before any stream starts (e.g. 503 when no AI provider
        // is configured). Surface that as the dedicated "unavailable" state, not a generic error.
        let active: ThreadSummary
        try {
            active = await ensureThread({mode: request.mode})
        } catch (e) {
            // 503 (provider unreachable) or 404 (agentic endpoints not registered — no provider bean)
            // both mean the copilot can't run: show the "unavailable" state, never a page redirect.
            if (is503(e) || is404(e)) unavailable.value = true
            else error.value = toErrorCode(e)
            return
        }

        error.value = null
        notice.value = null
        pendingConfirmation.value = null
        push({id: uid(), role: "USER", type: "TEXT", content: request.prompt})
        await runStream(`${base()}/${active.uid}/chat`, request)
    }

    /** Clears the unavailable state so the user can retry (e.g. after configuring a provider). */
    function retry(): void {
        unavailable.value = false
        error.value = null
        errorDetail.value = null
        notice.value = null
    }

    /** Re-runs the last chat/confirm turn — used by the empty-turn notice to retry without retyping. */
    async function retryLastTurn(): Promise<void> {
        if (!lastTurn || streaming.value) return
        await runStream(lastTurn.url, lastTurn.body)
    }

    /**
     * Resolves a pending proposal. APPROVE resumes & dispatches; REJECT records rejection.
     * The resumed turn runs a fresh model call, so it needs the same `providerId` the chat turn used.
     */
    async function confirm(decision: Decision, reason?: string, providerId?: string): Promise<void> {
        const active = thread.value
        const proposal = pendingConfirmation.value
        if (!active || !proposal) return

        const request: ConfirmActionRequest = {confirmationId: proposal.confirmationId, decision, reason, providerId}
        pendingConfirmation.value = null
        await runStream(`${base()}/${active.uid}/confirm`, request)
    }

    /** Cancels an in-flight stream (e.g. on unmount). */
    function cancel(): void {
        abort?.abort()
    }

    /** Starts a fresh conversation: drops the current thread/transcript back to the empty state. */
    function reset(): void {
        cancel()
        thread.value = null
        messages.value = []
        status.value = "IDLE"
        streaming.value = false
        error.value = null
        errorDetail.value = null
        notice.value = null
        pendingConfirmation.value = null
        unavailable.value = false
        activeAssistant = null
        lastTurn = null
        forgetThread()
    }

    /** Shared streaming driver for both chat and confirm turns. */
    async function runStream(url: string, body: unknown): Promise<void> {
        streaming.value = true
        status.value = "RUNNING"
        notice.value = null
        error.value = null
        errorDetail.value = null
        activeAssistant = null
        abort = new AbortController()
        lastTurn = {url, body}
        const countBefore = messages.value.length

        try {
            await streamSse({url, body, signal: abort.signal, onFrame: reduce})
            // The stream closed cleanly but added nothing to the transcript and left no proposal —
            // e.g. a transient provider hiccup returned only `done`. Surface a notice rather than
            // leaving the panel silent, so the user knows to retry. A streamed error event already
            // surfaces its own reason, so don't stack the empty-turn notice on top of it.
            if (messages.value.length === countBefore && !pendingConfirmation.value && !errorDetail.value && !error.value) {
                notice.value = "emptyTurn"
            }
        } catch (e) {
            if ((e as Error)?.name === "AbortError") return
            // 503 mid-stream (provider removed) → the unavailable state; otherwise a generic error.
            if (is503(e)) unavailable.value = true
            else error.value = toErrorCode(e)
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
            case AiEvent.ARTEFACT_DRAFT: {
                // A non-mutating artefact card (flow/dashboard/app YAML) rendered inline
                // in the transcript. Ends the current assistant bubble like a tool call does.
                activeAssistant = null
                push({id: uid(), role: "ASSISTANT", type: "ARTEFACT_DRAFT", draft: frame.data as ArtefactDraftEvent})
                break
            }
            case AiEvent.DONE: {
                // The resting state the stream closed into — authoritative.
                status.value = (frame.data as DoneEvent).status
                break
            }
            case AiEvent.ERROR: {
                // A terminal failure delivered mid-stream (e.g. the LLM call failed). Surface the
                // server's reason verbatim and drop back to IDLE; the stream completes right after.
                activeAssistant = null
                const message = (frame.data as ErrorEvent).message
                errorDetail.value = message || null
                if (!message) error.value = "request"
                status.value = "IDLE"
                break
            }
        }
    }

    function push(message: ChatMessage): void {
        messages.value.push(message)
    }

    function toErrorCode(e: unknown): ErrorCode {
        if (e instanceof SseHttpError) {
            if (e.status === 409) return "turnInProgress"
            if (e.status === 429) return "turnCap" // thread hit its turn limit — start a new chat
            return "request"
        }
        return "generic"
    }

    /** True when the failure is a 503 — from the SSE stream or the axios thread-create call. */
    function is503(e: unknown): boolean {
        if (e instanceof SseHttpError) return e.status === 503
        return (e as {response?: {status?: number}})?.response?.status === 503
    }

    /**
     * True when the failure is a 404 — the agentic AI endpoints aren't registered because no AI
     * provider is configured (`AiAgentController` is `@Requires(AiServiceManager)`). Treated like a
     * 503 (copilot unavailable), not the global not-found page.
     */
    function is404(e: unknown): boolean {
        if (e instanceof SseHttpError) return e.status === 404
        const err = e as {status?: number; response?: {status?: number}}
        return err?.status === 404 || err?.response?.status === 404
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
        errorDetail,
        notice,
        pendingConfirmation,
        unavailable,
        canSend,
        // actions
        ensureThread,
        loadThread,
        restoreThread,
        sendChat,
        confirm,
        cancel,
        reset,
        retry,
        retryLastTurn,
    }
}

export type {Mode, ScopeBinding}
