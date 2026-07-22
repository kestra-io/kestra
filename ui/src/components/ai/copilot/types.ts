/**
 * Wire types for the AI Copilot v2 agentic loop.
 *
 * These mirror the backend types on branch `feat/ai-copilot-v2-agentic-loop`:
 *   - io.kestra.webserver.services.ai.agent.data.{ApiChatTurnRequest, ApiThreadDetail, ApiThreadSummary, AgentEvents}
 *   - io.kestra.core.ai.agent.models.{AgentMode, AgentThreadStatus, AgentToolCall, …}
 *
 * (ScopeBinding is now frontend-local — used by routeScope.ts to build the free-form
 * `additionalContext`; the backend no longer has a structured in-focus type.)
 *
 * Keep this file in sync with those records — it is the single source of truth
 * for the shapes the frontend exchanges with `…/ai/threads`.
 */

/** Conversation mode. */
export type Mode = "ASK" | "EDIT" | "PLAN"

/** The resting/working state of a thread — the single source of truth for what the UI may do next. */
export type ThreadStatus = "IDLE" | "RUNNING" | "AWAITING_CONFIRMATION"

export type MessageRole = "USER" | "ASSISTANT" | "TOOL" | "SYSTEM"

export type MessageType = "TEXT" | "TOOL_CALL" | "TOOL_RESULT" | "PROPOSED_ACTION" | "ARTEFACT_DRAFT" | "CANCELLED"

export type ToolFamily = "READ" | "MUTATE" | "ACT"

export type ToolKind = "PLATFORM" | "AUTHORING"

export type Decision = "APPROVE" | "REJECT"

/** The artefact a turn is bound to / focused on. */
export interface ScopeBinding {
    kind: "FLOW" | "NAMESPACE" | "EXECUTION"
    namespace?: string | null
    flowId?: string | null
    executionId?: string | null
}

export interface ToolCall {
    id?: string | null
    kind: ToolKind
    tool: string
    family?: ToolFamily | null
    arguments: Record<string, unknown>
}

/* ------------------------------------------------------------------ *
 * Requests
 * ------------------------------------------------------------------ */

export interface CreateThreadRequest {
    mode?: Mode | null
    title?: string | null
    scope?: ScopeBinding | null
}

export interface ChatTurnRequest {
    prompt: string
    mode?: Mode | null
    /**
     * Free-form, per-turn context (e.g. what the user is currently viewing). The backend renders it
     * into the model input for this turn only — it is not persisted. Replaces the old `inFocus`.
     */
    additionalContext?: Record<string, unknown> | null
    providerId?: string | null
}

export interface ConfirmActionRequest {
    confirmationId: string
    decision: Decision
    reason?: string | null
    providerId?: string | null
}

/* ------------------------------------------------------------------ *
 * Responses
 * ------------------------------------------------------------------ */

export interface ThreadSummary {
    uid: string
    title?: string | null
    mode: Mode
    scope?: ScopeBinding | null
    status: ThreadStatus
    createdAt: string
    updatedAt: string
    lastTurnAt?: string | null
}

export interface MessageView {
    uid: string
    role: MessageRole
    type: MessageType
    content?: string | null
    toolCall?: ToolCall | null
    toolResult?: Record<string, unknown> | null
    /** Present on ARTEFACT_DRAFT messages — the drafted artefact to render. */
    draft?: ArtefactDraftEvent | null
    createdAt: string
}

export interface ThreadDetail {
    uid: string
    title?: string | null
    mode: Mode
    scope?: ScopeBinding | null
    status: ThreadStatus
    /** Set only when status is AWAITING_CONFIRMATION — lets a reload resume the suspended turn. */
    pendingConfirmationId?: string | null
    messages: MessageView[]
}

/* ------------------------------------------------------------------ *
 * SSE events (from `…/chat` and `…/confirm`)
 * Event name → payload. Matches AgentEvents constants exactly.
 * ------------------------------------------------------------------ */

export const AiEvent = {
    TOKEN: "token",
    TOOL_CALL: "tool_call",
    TOOL_RESULT: "tool_result",
    PROPOSED_ACTION: "proposed_action",
    ARTEFACT_DRAFT: "artefact_draft",
    DONE: "done",
    ERROR: "error",
} as const

export type AiEventName = (typeof AiEvent)[keyof typeof AiEvent]

export interface TokenEvent {
    text: string
}

export interface ToolCallEvent {
    tool: string
    /** "PLATFORM" (read/act tools) or "AUTHORING" (draft-producing sub-agents). */
    kind?: ToolKind | null
    family?: ToolFamily | null
    arguments: Record<string, unknown>
}

/** Kind of artefact an authoring sub-agent drafts. */
export type ArtefactKind = "FLOW" | "DASHBOARD" | "APP"

/**
 * A non-mutating artefact (flow / dashboard / app YAML) drafted by an authoring
 * sub-agent. Nothing is saved server-side — the user reviews it as a card.
 */
export interface ArtefactDraftEvent {
    draftId: string
    kind: ArtefactKind
    yaml: string
    /** Whether the draft passed platform validation. */
    valid: boolean
    /** Validation violations to fix, when `valid` is false. */
    constraints?: string | null
}

export interface ToolResultEvent {
    /** Present on the live stream; absent on thread reload (the persisted map has no `tool` — the
     *  name comes from the paired tool-call instead). */
    tool?: string
    /** "ok", "error" (the tool threw; the turn continues), or "rejected". */
    outcome: string
    /**
     * Detail persisted with the result — present on thread reload, not on the live stream (the SSE
     * tool_result event carries only `tool` + `outcome`). `result` on success, `error` on a thrown
     * tool, `reason` on a user rejection.
     */
    result?: unknown
    error?: string | null
    reason?: string | null
}

/** A single step of a Plan-mode plan card. */
export interface ProposedStep {
    /** What the step does, e.g. "Mock external task outputs". */
    title: string
    /** Optional target/detail line, e.g. the file path "tests/…​.test.yml". */
    detail?: string | null
}

export interface ProposedActionEvent {
    confirmationId: string
    /** null for Plan-mode plan cards. */
    tool?: string | null
    family?: ToolFamily | null
    /** Card heading, e.g. "Add test coverage". Falls back to a generic title when absent. */
    title?: string | null
    summary: string
    /** Structured Plan steps, when the backend provides them (rendered as a numbered list). */
    steps?: ProposedStep[] | null
    arguments?: Record<string, unknown> | null
}

export interface DoneEvent {
    /** The resting ThreadStatus the stream closed into. */
    status: ThreadStatus
}

/**
 * A terminal failure delivered as an SSE event (not an HTTP/transport error), so the reason reaches
 * the client even after the stream has committed. The stream completes right after; no `done` follows.
 */
export interface ErrorEvent {
    message: string
}

/** A parsed SSE frame: an event name plus its already-JSON-parsed payload. */
export interface AiSseFrame {
    event: AiEventName
    data: unknown
}
