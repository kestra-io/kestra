<template>
    <div v-if="message.role === 'USER'" class="copilot-msg copilot-msg-user">
        <div class="copilot-bubble copilot-bubble-user">
            <template v-for="(segment, index) in userSegments" :key="index">
                <pre v-if="segment.code" class="copilot-user-code" data-test="copilot-user-code">{{ segment.content }}</pre>
                <KsText v-else size="small" class="copilot-bubble-text">{{ segment.content }}</KsText>
            </template>
        </div>
    </div>

    <!-- A display-only line noting a context (focus) change; centred + muted, not a chat bubble.
         The resource id is a monospace code token, matching how ids read elsewhere in the copilot. -->
    <div v-else-if="message.type === 'CONTEXT' && message.context" class="copilot-msg copilot-context-notice" data-test="copilot-context-notice">
        <KsText size="small" class="copilot-context-notice-text">
            <i18n-t
                :keypath="message.context.action === 'added' ? 'ai.copilot.contextAdded' : 'ai.copilot.contextRemoved'"
                scope="global"
                tag="span"
            >
                <template #type>{{ $t(message.context.noun) }}</template>
                <template #id><code class="copilot-context-id">{{ message.context.id }}</code></template>
            </i18n-t>
        </KsText>
    </div>

    <!-- A display-only line noting the AI provider/model was switched mid-conversation; same quiet
         centred treatment as the context notice above. -->
    <div v-else-if="message.type === 'MODEL_CHANGED' && message.modelChange" class="copilot-msg copilot-context-notice" data-test="copilot-model-notice">
        <KsText size="small" class="copilot-context-notice-text">
            {{ $t("ai.copilot.modelChanged", {model: message.modelChange.label}) }}
        </KsText>
    </div>

    <div v-else-if="message.type === 'TEXT'" class="copilot-msg copilot-msg-assistant">
        <div class="copilot-bubble copilot-bubble-assistant" data-test="copilot-assistant-text">
            <KsMarkdown v-if="message.content" :content="message.content" />
        </div>
    </div>

    <div v-else-if="message.type === 'TOOL_CALL'" class="copilot-msg copilot-tool" data-test="copilot-tool-call">
        <KsCollapse v-model="expanded">
            <KsCollapseItem name="tool">
                <!-- Title via slot so it renders at the same small/secondary treatment as the
                     rest of the transcript (the default collapse header is larger + primary). -->
                <template #title>
                    <span class="copilot-tool-title">
                        <!-- Spins only while the tool is still executing (no result yet), so an
                             in-flight step reads as "in progress" instead of sitting blank. -->
                        <KsIcon v-if="isRunning" class="copilot-tool-spinner" aria-hidden="true">
                            <Loading />
                        </KsIcon>
                        <KsText size="small" class="copilot-tool-label">
                            {{ $t("ai.copilot.toolCall", {tool: message.toolCall?.tool ?? ""}) }}
                        </KsText>
                    </span>
                </template>
                <pre class="copilot-tool-args">{{ argsJson }}</pre>
            </KsCollapseItem>
        </KsCollapse>
    </div>

    <div v-else-if="message.type === 'TOOL_RESULT'" class="copilot-msg copilot-tool-result" data-test="copilot-tool-result">
        <div class="copilot-tool-result-row">
            <KsIcon class="copilot-tool-result-icon" :class="isOk ? 'is-ok' : 'is-error'">
                <CheckCircleOutline v-if="isOk" />
                <CloseCircleOutline v-else />
            </KsIcon>
            <KsText size="small" class="copilot-tool-label">
                {{ $t(`ai.copilot.toolResult.${outcome}`, {tool: toolName}) }}
            </KsText>
        </div>

        <!-- Result payload / error / rejection reason — present on thread reload (the live stream
             carries only the outcome). Collapsed by default, like the tool-call args. -->
        <KsCollapse v-if="resultDetail" v-model="expandedResult" data-test="copilot-tool-result-detail">
            <KsCollapseItem name="result">
                <template #title>
                    <KsText size="small" class="copilot-tool-label">{{ $t("ai.copilot.toolResult.detail") }}</KsText>
                </template>
                <pre class="copilot-tool-args">{{ resultDetail }}</pre>
            </KsCollapseItem>
        </KsCollapse>
    </div>

    <div v-else-if="message.type === 'ARTEFACT_DRAFT' && message.draft" class="copilot-msg copilot-msg-assistant">
        <CopilotArtefactDraft :draft="message.draft" />
    </div>

    <!-- A past proposal, read-only in the transcript. The still-pending one is rendered by the
         interactive ProposedActionCard in CopilotChat, so `isPending` suppresses it here (no double). -->
    <div
        v-else-if="message.type === 'PROPOSED_ACTION' && !isPending"
        class="copilot-msg copilot-msg-assistant"
        data-test="copilot-proposed-history"
    >
        <ProposedActionCard :action="historicalProposal" resolved />
    </div>

    <div v-else-if="message.type === 'CANCELLED'" class="copilot-msg copilot-msg-cancelled" data-test="copilot-cancelled">
        <KsText size="small" class="copilot-cancelled-label">{{ $t("ai.copilot.cancelled") }}</KsText>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"
    import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
    import CloseCircleOutline from "vue-material-design-icons/CloseCircleOutline.vue"
    import Loading from "vue-material-design-icons/Loading.vue"
    import CopilotArtefactDraft from "./CopilotArtefactDraft.vue"
    import ProposedActionCard from "./ProposedActionCard.vue"
    import type {ProposedActionEvent} from "./types"
    import type {ChatMessage} from "./useAiChat"

    const props = defineProps<{
        message: ChatMessage
        /** True when this PROPOSED_ACTION is the active one (shown by CopilotChat's interactive card). */
        isPending?: boolean
        /** True when this TOOL_CALL is still executing (no result yet) — drives the running spinner. */
        isRunning?: boolean
    }>()

    // The user prompt rendered literally, split on ``` fences only — full markdown would mangle
    // pasted code (a YAML `# comment` must not become a heading) (kestra-io/kestra-ee#10420).
    const userSegments = computed<{code: boolean; content: string}[]>(() => {
        const content = props.message.content ?? ""
        const segments: {code: boolean; content: string}[] = []
        const push = (code: boolean, text: string) => {
            const value = code ? text : text.trim()
            if (value.trim()) segments.push({code, content: value})
        }
        const fence = /```[^\n]*\n([\s\S]*?)(?:\n?```|$)/g
        let cursor = 0
        for (const match of content.matchAll(fence)) {
            push(false, content.slice(cursor, match.index))
            push(true, match[1])
            cursor = (match.index ?? 0) + match[0].length
        }
        push(false, content.slice(cursor))
        return segments
    })

    // Display-only proposal for a historical PROPOSED_ACTION message: the live event carries the full
    // proposal; a reloaded one carries the summary as `content` and the held tool as `toolCall`.
    const historicalProposal = computed<ProposedActionEvent>(() =>
        props.message.proposedAction ?? {
            confirmationId: "",
            summary: props.message.content ?? "",
            tool: props.message.toolCall?.tool ?? null,
            family: props.message.toolCall?.family ?? null,
            arguments: props.message.toolCall?.arguments ?? null,
        },
    )

    const expanded = ref<string[]>([])
    const expandedResult = ref<string[]>([])

    const argsJson = computed(() => JSON.stringify(props.message.toolCall?.arguments ?? {}, null, 2))
    // On reload the tool name lives on the paired toolCall (the persisted result map has no `tool`);
    // on the live stream it's on the result event itself.
    const toolName = computed(() => props.message.toolResult?.tool ?? props.message.toolCall?.tool ?? "")
    // Backend outcomes: "ok" (tool ran), "error" (tool threw, turn continues), "rejected" (user declined).
    // Anything unexpected falls back to "rejected" so we never render a raw/missing label.
    const outcome = computed(() => {
        const value = props.message.toolResult?.outcome
        return value === "ok" || value === "error" ? value : "rejected"
    })
    const isOk = computed(() => outcome.value === "ok")

    // The detail to show under a tool result: the returned payload on success, the message on an
    // error, the reason on a rejection. Null when there's nothing extra (e.g. a live turn, which
    // streams only the outcome) so the collapsible is hidden.
    const resultDetail = computed<string | null>(() => {
        const toolResult = props.message.toolResult
        if (!toolResult) return null
        if (outcome.value === "error") return toolResult.error ?? null
        if (outcome.value === "rejected") return toolResult.reason ?? null
        if (toolResult.result == null) return null
        return typeof toolResult.result === "string"
            ? toolResult.result
            : JSON.stringify(toolResult.result, null, 2)
    })
</script>

<style scoped>
    .copilot-msg {
        margin-bottom: var(--ks-spacing-3);
    }

    .copilot-msg-user {
        display: flex;
        justify-content: flex-end;
    }

    .copilot-msg-assistant {
        display: flex;
        justify-content: flex-start;
    }

    /* Assistant-side blocks (text bubble, draft card, past proposal) span the transcript column, so
       they end on the same right edge as the full-width tool strip instead of a ragged, content-
       dependent one (kestra-io/kestra#18388). `min-width: 0` keeps a wide code block or long id from
       stretching the block past the column. */
    .copilot-msg-assistant > * {
        flex: 1 1 auto;
        min-width: 0;
    }

    /* Context-change notice: a quiet, centred line, not a chat bubble. It's not selectable prose, so
       the pointer stays the default arrow rather than the text I-beam. */
    .copilot-context-notice {
        display: flex;
        justify-content: center;
        text-align: center;
        cursor: default;
    }

    .copilot-context-notice-text {
        --kel-text-color: var(--ks-text-secondary);
    }

    /* The resource id inside a context notice — a quiet monospace token, colour inherited from the line. */
    .copilot-context-id {
        font-family: var(--ks-font-family-mono);
    }

    .copilot-msg-cancelled {
        display: flex;
        justify-content: center;
    }

    .copilot-cancelled-label {
        --kel-text-color: var(--ks-text-muted);
        font-style: italic;
    }

    .copilot-bubble-user {
        max-width: 85%;
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-elevated);
    }

    /* Pasted multi-line text keeps its line breaks and indentation instead of collapsing into
       one long line (kestra-io/kestra-ee#10420). */
    .copilot-bubble-text {
        white-space: pre-wrap;
        word-break: break-word;
    }

    /* A ``` fenced segment of the user prompt, shown as a literal code block. Wraps instead of
       scrolling so the whole snippet stays readable inside the narrow bubble. */
    .copilot-user-code {
        margin: var(--ks-spacing-1) 0;
        padding: var(--ks-spacing-2);
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-sm);
        background: var(--ks-bg-base);
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-sm);
        line-height: 1.5;
        white-space: pre-wrap;
        word-break: break-word;
    }

    /* Assistant replies get their own left-aligned bubble (surface fill) so they read as
       styled responses rather than plain text. */
    .copilot-bubble-assistant {
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-subtle);
        /* Copilot body copy sits at sm (12px), matching user bubbles / tool strip / plan card.
           .ks-markdown sets no font-size of its own, so this cascades into its paragraphs,
           list items and inline code — keeping every message the same base size. */
        font-size: var(--ks-font-size-sm);
        line-height: 1.5;
    }

    .copilot-tool-args {
        margin: 0;
        font-family: monospace;
        white-space: pre-wrap;
        word-break: break-word;
        color: var(--ks-text-secondary);
    }

    /* Body copy (user bubble) and the tool strip sit at sm (12px), uniform with the rest. */
    .copilot-bubble-text,
    .copilot-tool-label {
        font-size: var(--ks-font-size-sm);
    }

    /* Tool call header + tool result share one subdued treatment (secondary),
       so the whole "tool activity" strip reads uniformly. Status is carried by the icon. */
    .copilot-tool-label {
        --kel-text-color: var(--ks-text-secondary);
    }

    .copilot-tool-title {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }

    /* The running spinner sits at the same neutral weight as the tool label; motion carries the
       "in progress" meaning (completed/failed states use a coloured check/cross instead). */
    .copilot-tool-spinner {
        margin-right: var(--ks-spacing-1);
        color: var(--ks-icon-default);
        animation: copilot-tool-spin 1s linear infinite;
    }

    @keyframes copilot-tool-spin {
        to {
            transform: rotate(360deg);
        }
    }

    .copilot-tool-result-row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }

    .copilot-tool-result-icon.is-ok {
        color: var(--ks-text-success);
    }

    .copilot-tool-result-icon.is-error {
        color: var(--ks-text-error);
    }
</style>
