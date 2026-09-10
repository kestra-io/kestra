<template>
    <!-- Figma confirm card: lighter elevated header/footer bands around a darker body. -->
    <div class="proposed-action" data-test="copilot-proposed-action">
        <div class="proposed-action-header">
            <KsText size="small" class="proposed-action-title">{{ title }}</KsText>
            <KsTag v-if="!isPlan && action.family" size="small">{{ action.family }}</KsTag>
            <KsText v-if="!resolved" size="small" class="proposed-action-status">{{ $t("ai.copilot.confirm.pending") }}</KsText>
        </div>

        <div class="proposed-action-body">
            <!-- Structured plan steps when the backend provides them; otherwise the text summary. -->
            <ol v-if="steps.length" class="proposed-action-steps">
                <li v-for="(step, index) in steps" :key="index" class="proposed-step">
                    <span class="proposed-step-number">{{ index + 1 }}</span>
                    <div class="proposed-step-body">
                        <KsText size="small" class="proposed-step-title">{{ step.title }}</KsText>
                        <span v-if="step.detail" class="proposed-step-detail">{{ step.detail }}</span>
                    </div>
                </li>
            </ol>
            <KsText v-else size="small" class="proposed-action-summary">{{ action.summary }}</KsText>

            <!-- Identifying arguments (namespace/flowId, executionId, …) so the user sees the target. -->
            <dl v-if="argEntries.length" class="proposed-action-args" data-test="copilot-proposed-args">
                <div v-for="[key, value] in argEntries" :key="key" class="proposed-action-arg">
                    <dt class="proposed-action-arg-key">{{ key }}</dt>
                    <dd class="proposed-action-arg-value">{{ value }}</dd>
                </div>
            </dl>

            <!-- The YAML/source body of a mutating action — the one thing a text summary can't convey —
                 rendered as a diff against the matching flow's current content, so the user sees exactly
                 what will change before approving. -->
            <DiffView
                v-if="proposedSource !== null"
                class="proposed-action-diff"
                data-test="copilot-proposed-diff"
                :oldValue="currentFlowSource ?? ''"
                :newValue="proposedSource"
            />
        </div>

        <div v-if="!resolved" class="proposed-action-footer">
            <KsButton
                text
                :disabled="disabled"
                class="proposed-action-btn proposed-action-reject"
                data-test="copilot-reject"
                @click="emit('reject')"
            >
                {{ isPlan ? $t("ai.copilot.confirm.revise") : $t("ai.copilot.confirm.reject") }}
            </KsButton>
            <KsButton
                type="primary"
                :disabled="disabled"
                class="proposed-action-btn"
                data-test="copilot-approve"
                @click="emit('approve')"
            >
                {{ isPlan ? $t("ai.copilot.confirm.approveExecute") : $t("ai.copilot.confirm.approve") }}
            </KsButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import DiffView from "./DiffView.vue"
    import type {ProposedActionEvent, ProposedStep} from "./types"

    const props = defineProps<{
        action: ProposedActionEvent
        /** Disabled once a decision has been sent (stream in flight). */
        disabled?: boolean
        /** Historical (already-decided) proposal in the transcript: no footer actions, no pending status. */
        resolved?: boolean
        /** The current source of the flow this action targets, when known — the diff's "before" side.
         *  Absent/empty renders the proposed source as a pure addition. */
        currentFlowSource?: string | null
    }>()

    const emit = defineEmits<{
        (e: "approve"): void
        (e: "reject"): void
    }>()

    const {t} = useI18n()

    // A Plan-mode plan card carries no concrete tool call.
    const isPlan = computed(() => !props.action.tool)
    const steps = computed<ProposedStep[]>(() => props.action.steps ?? [])
    const title = computed(
        () => props.action.title ?? t(isPlan.value ? "ai.copilot.confirm.planTitle" : "ai.copilot.confirm.actionTitle"),
    )

    /** Longest scalar arg we'll show inline — anything above is a verbose payload (e.g. a YAML body). */
    const MAX_ARG_LENGTH = 120

    /** Argument keys, in preference order, that carry the full YAML/source body of a mutating action —
     *  shown as a diff instead of in the identifying-args list below. */
    const SOURCE_ARG_KEYS = ["body", "source", "yaml"] as const

    /**
     * The identifying arguments shown under the summary so the user sees exactly what will run (e.g.
     * namespace/flowId, executionId). Scalars only — objects/arrays, the source/body argument (rendered
     * as a diff instead) and long values are omitted to keep the card compact; empty for plan cards
     * (no concrete tool call).
     */
    const argEntries = computed<[string, string][]>(() => {
        if (isPlan.value || !props.action.arguments) return []
        return Object.entries(props.action.arguments)
            .filter(([key]) => !(SOURCE_ARG_KEYS as readonly string[]).includes(key))
            .filter(([, value]) => typeof value === "string" || typeof value === "number" || typeof value === "boolean")
            .map(([key, value]) => [key, String(value)] as [string, string])
            .filter(([, value]) => value.length > 0 && value.length <= MAX_ARG_LENGTH)
    })

    /**
     * The proposed YAML/source body, when this action mutates one — a known key (`body`/`source`/`yaml`)
     * takes priority; otherwise the sole argument long enough that `argEntries` would drop it as "too
     * verbose to show inline" is assumed to be it. Null when there's nothing to diff (a plan card, a
     * mutating action with no source payload, or more than one long argument to choose from).
     */
    const proposedSource = computed<string | null>(() => {
        if (isPlan.value || !props.action.arguments) return null
        for (const key of SOURCE_ARG_KEYS) {
            const value = props.action.arguments[key]
            if (typeof value === "string" && value.length > 0) return value
        }
        const longValues = Object.entries(props.action.arguments)
            .filter(([key]) => !(SOURCE_ARG_KEYS as readonly string[]).includes(key))
            .map(([, value]) => value)
            .filter((value): value is string => typeof value === "string" && value.length > MAX_ARG_LENGTH)
        return longValues.length === 1 ? longValues[0] : null
    })
</script>

<style scoped>
    .proposed-action {
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        /* Darker body; header/footer bands sit a shade lighter on top (Figma). */
        background: var(--ks-bg-base);
        /* Clip the tinted header/footer bands to the rounded corners. */
        overflow: hidden;
    }

    /* Header + footer share the lighter elevated fill; body stays on the darker base. */
    .proposed-action-header {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
        background: var(--ks-bg-elevated);
        border-bottom: 1px solid var(--ks-border-subtle);
    }

    /* Card copy sits one step up from the transcript's xs, at sm (12px). */
    .proposed-action-title,
    .proposed-action-status,
    .proposed-action-summary,
    .proposed-step-title {
        font-size: var(--ks-font-size-sm);
    }

    .proposed-action-title {
        font-weight: 600;
    }

    .proposed-action-status {
        margin-left: auto;
        --kel-text-color: var(--ks-text-secondary);
    }

    .proposed-action-body {
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
        background: var(--ks-bg-base);
    }

    .proposed-action-summary {
        display: block;
        white-space: pre-line;
        --kel-text-color: var(--ks-text-secondary);
    }

    .proposed-action-args {
        margin: var(--ks-spacing-2) 0 0;
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
    }

    .proposed-action-arg {
        display: flex;
        gap: var(--ks-spacing-2);
        font-size: var(--ks-font-size-xs);
    }

    .proposed-action-arg-key {
        flex: 0 0 auto;
        min-width: 5rem;
        color: var(--ks-text-secondary);
    }

    .proposed-action-arg-value {
        margin: 0;
        min-width: 0;
        font-family: var(--ks-font-family-mono);
        color: var(--ks-text-primary);
        word-break: break-word;
    }

    .proposed-action-diff {
        margin-top: var(--ks-spacing-2);
    }

    .proposed-action-steps {
        display: flex;
        flex-direction: column;
        margin: 0;
        padding: 0;
        list-style: none;
    }

    .proposed-step {
        display: flex;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-3) 0;
    }

    .proposed-step + .proposed-step {
        border-top: 1px solid var(--ks-border-subtle);
    }

    .proposed-step-number {
        flex-shrink: 0;
        width: var(--ks-spacing-5);
        height: var(--ks-spacing-5);
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: 50%;
        background: var(--ks-bg-tag);
        color: var(--ks-text-secondary);
    }

    .proposed-step-body {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
    }

    .proposed-step-detail {
        font-family: monospace;
        color: var(--ks-text-muted);
        word-break: break-word;
    }

    .proposed-action-footer {
        display: flex;
        align-items: center;
        /* Both actions on the right (Figma). */
        justify-content: flex-end;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
        background: var(--ks-bg-elevated);
        border-top: 1px solid var(--ks-border-subtle);
    }

    /* Footer buttons come down from the 14px default to sm (12px) so they sit closer to the copy. */
    .proposed-action-btn {
        font-size: var(--ks-font-size-sm);
    }

    /* "Reply to revise" reads as a subdued secondary link next to the primary action. */
    .proposed-action-reject {
        --ks-button-text-color: var(--ks-text-secondary);
        color: var(--ks-text-secondary);
    }
</style>
