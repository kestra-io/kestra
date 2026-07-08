<template>
    <KsCard shadow="never" class="proposed-action" data-test="copilot-proposed-action">
        <div class="proposed-action-header">
            <KsText size="small" class="proposed-action-title">{{ title }}</KsText>
            <KsTag v-if="!isPlan && action.family" size="small">{{ action.family }}</KsTag>
            <KsText size="small" class="proposed-action-status">{{ t("ai.copilot.confirm.pending") }}</KsText>
        </div>

        <!-- Structured plan steps when the backend provides them; otherwise the text summary. -->
        <ol v-if="steps.length" class="proposed-action-steps">
            <li v-for="(step, index) in steps" :key="index" class="proposed-step">
                <span class="proposed-step-number">{{ index + 1 }}</span>
                <div class="proposed-step-body">
                    <KsText size="small">{{ step.title }}</KsText>
                    <span v-if="step.detail" class="proposed-step-detail">{{ step.detail }}</span>
                </div>
            </li>
        </ol>
        <KsText v-else size="small" class="proposed-action-summary">{{ action.summary }}</KsText>

        <div class="proposed-action-footer">
            <KsButton
                type="text"
                :disabled="disabled"
                data-test="copilot-reject"
                @click="emit('reject')"
            >
                {{ isPlan ? t("ai.copilot.confirm.revise") : t("ai.copilot.confirm.reject") }}
            </KsButton>
            <KsButton
                type="primary"
                :disabled="disabled"
                data-test="copilot-approve"
                @click="emit('approve')"
            >
                {{ isPlan ? t("ai.copilot.confirm.approveExecute") : t("ai.copilot.confirm.approve") }}
            </KsButton>
        </div>
    </KsCard>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import type {ProposedActionEvent, ProposedStep} from "./types"

    const props = defineProps<{
        action: ProposedActionEvent
        /** Disabled once a decision has been sent (stream in flight). */
        disabled?: boolean
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
</script>

<style scoped>
    .proposed-action {
        border: 1px solid var(--ks-border-default);
        background: var(--ks-bg-surface);
    }

    .proposed-action-header {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        margin-bottom: var(--ks-spacing-3);
    }

    .proposed-action-title {
        font-weight: 600;
    }

    .proposed-action-status {
        margin-left: auto;
        color: var(--ks-text-secondary);
    }

    .proposed-action-summary {
        display: block;
        white-space: pre-line;
        color: var(--ks-text-secondary);
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
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        margin-top: var(--ks-spacing-4);
    }
</style>
