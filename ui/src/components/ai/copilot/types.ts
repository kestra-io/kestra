/**
 * Wire types for the AI Copilot v2 agentic loop.
 *
 * These mirror the backend DTOs on branch `feat/ai-copilot-v2-agentic-loop`:
 *   - io.kestra.webserver.services.ai.agent.dto.AgentDtos
 *   - io.kestra.webserver.services.ai.agent.dto.AgentEvents
 *   - io.kestra.webserver.services.ai.agent.domain.{Mode,ThreadStatus,ScopeBinding,ToolCall,...}
 *
 * Keep this file in sync with those records — it is the single source of truth
 * for the shapes the frontend exchanges with `…/ai/threads`.
 */

/** Conversation mode. NOTE: the Figma design labels EDIT as "Build". */
export type Mode = "ASK" | "EDIT" | "PLAN"

/** The resting/working state of a thread — the single source of truth for what the UI may do next. */
export type ThreadStatus = "IDLE" | "RUNNING" | "AWAITING_CONFIRMATION"

export type MessageRole = "USER" | "ASSISTANT" | "TOOL" | "SYSTEM"

export type MessageType = "TEXT" | "TOOL_CALL" | "TOOL_RESULT" | "PROPOSED_ACTION"

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
    inFocus?: ScopeBinding | null
    providerId?: string | null
}

export interface ConfirmActionRequest {
    confirmationId: string
    decision: Decision
    reason?: string | null
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
    createdAt: string
}

export interface ThreadDetail {
    uid: string
    title?: string | null
    mode: Mode
    scope?: ScopeBinding | null
    status: ThreadStatus
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
    DONE: "done",
} as const

export type AiEventName = (typeof AiEvent)[keyof typeof AiEvent]

export interface TokenEvent {
    text: string
}

export interface ToolCallEvent {
    tool: string
    family?: ToolFamily | null
    arguments: Record<string, unknown>
}

export interface ToolResultEvent {
    tool: string
    /** "ok" or "rejected". */
    outcome: string
}

export interface ProposedActionEvent {
    confirmationId: string
    /** null for Plan-mode plan cards. */
    tool?: string | null
    family?: ToolFamily | null
    summary: string
    arguments?: Record<string, unknown> | null
}

export interface DoneEvent {
    /** The resting ThreadStatus the stream closed into. */
    status: ThreadStatus
}

/** A parsed SSE frame: an event name plus its already-JSON-parsed payload. */
export interface AiSseFrame {
    event: AiEventName
    data: unknown
}
