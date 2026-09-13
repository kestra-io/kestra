/**
 * Wire types for the AI Copilot v2 agentic loop (`…/ai/threads`).
 *
 * Enum/union wire types come straight from the generated `@kestra-io/kestra-sdk` and are used
 * directly here and at the call sites (`AgentMode`, `AgentThreadStatus`, `AgentMessageRole/Type`,
 * `AgentToolFamily`, `AgentToolCallKind`, `ApiDecision`, `ArtefactKind`) — the SDK is regenerated from
 * the backend OpenAPI, so a new mode/status/message type flows in automatically instead of drifting.
 *
 * The object shapes below stay hand-written on purpose: the generated `Api*`/`Agent*` DTOs make every
 * field optional and type `arguments`/`toolResult` as a doubly-nested map (a codegen artifact of the
 * backend's `Map<String, Object>`), both looser than what the UI actually receives. We keep tighter
 * local shapes that reference the SDK enums above, plus the SSE frame types the SDK doesn't model.
 *
 * (ScopeBinding is frontend-local — used by routeScope.ts to build the free-form `additionalContext`;
 * the backend has no structured in-focus type.)
 */

import type {
    AgentMode,
    AgentThreadStatus,
    AgentMessageRole,
    AgentMessageType,
    AgentToolFamily,
    AgentToolCallKind,
    ApiDecision,
    ArtefactKind,
} from "@kestra-io/kestra-sdk"

/** The artefact a turn is bound to / focused on. */
export interface ScopeBinding {
    kind: "FLOW" | "NAMESPACE" | "EXECUTION" | "DASHBOARD" | "APP" | "TEST" | "BLUEPRINT" | "PLUGIN"
    namespace?: string | null
    flowId?: string | null
    executionId?: string | null
    dashboardId?: string | null
    appId?: string | null
    testId?: string | null
    blueprintId?: string | null
    pluginId?: string | null
}

/**
 * A removable context pill's identity — the scope field it drops from the focus when dismissed
 * (`"namespace" | "flowId" | "executionId"`). Reuses `ScopeBinding`'s own keys so the two can't drift.
 */
export type ContextPart = keyof Omit<ScopeBinding, "kind">

export interface ToolCall {
    id?: string | null
    kind: AgentToolCallKind
    tool: string
    family?: AgentToolFamily | null
    arguments: Record<string, unknown>
}

/* ------------------------------------------------------------------ *
 * Requests
 * ------------------------------------------------------------------ */

export interface CreateThreadRequest {
    mode?: AgentMode | null
    title?: string | null
    scope?: ScopeBinding | null
}

export interface ChatTurnRequest {
    prompt: string
    mode?: AgentMode | null
    /**
     * Free-form, per-turn context (e.g. what the user is currently viewing). The backend renders it
     * into the model input for this turn only — it is not persisted. Replaces the old `inFocus`.
     */
    additionalContext?: Record<string, unknown> | null
    providerId?: string | null
}

export interface ConfirmActionRequest {
    confirmationId: string
    decision: ApiDecision
    reason?: string | null
    providerId?: string | null
}

/* ------------------------------------------------------------------ *
 * Responses
 * ------------------------------------------------------------------ */

export interface ThreadSummary {
    uid: string
    title?: string | null
    mode: AgentMode
    scope?: ScopeBinding | null
    status: AgentThreadStatus
    createdAt: string
    updatedAt: string
    lastTurnAt?: string | null
}

export interface MessageView {
    uid: string
    role: AgentMessageRole
    type: AgentMessageType
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
    mode: AgentMode
    scope?: ScopeBinding | null
    status: AgentThreadStatus
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
    kind?: AgentToolCallKind | null
    family?: AgentToolFamily | null
    arguments: Record<string, unknown>
}

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
    family?: AgentToolFamily | null
    /** Card heading, e.g. "Add test coverage". Falls back to a generic title when absent. */
    title?: string | null
    summary: string
    /** Structured Plan steps, when the backend provides them (rendered as a numbered list). */
    steps?: ProposedStep[] | null
    arguments?: Record<string, unknown> | null
}

export interface DoneEvent {
    /** The resting thread status the stream closed into. */
    status: AgentThreadStatus
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
