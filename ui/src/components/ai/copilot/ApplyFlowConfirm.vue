<template>
    <div class="apply-confirm" data-test="copilot-apply-confirm">
        <div class="apply-confirm-flow">
            <KsIcon class="apply-confirm-icon">
                <AiMenuIcon :size="20" />
            </KsIcon>
            <div class="apply-confirm-text">
                <div class="apply-confirm-line">
                    <KsText class="apply-confirm-id">
                        {{ namespace }}<span class="apply-confirm-separator">/</span>{{ id }}
                    </KsText>
                    <KsTag size="small" type="info">{{ $t("draft") }}</KsTag>
                </div>
                <KsText size="small" type="secondary" class="apply-confirm-hint">
                    <strong>{{ $t("ai.copilot.draft.applyDraftTitle") }}</strong>
                    {{ $t("ai.copilot.draft.applyDraftHint") }}
                </KsText>
            </div>
        </div>
        <FlowChangeSummary :diff="diffFlowSources(before || undefined, after)" />
        <div class="apply-confirm-diff">
            <DiffView :oldValue="before" :newValue="after" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {diffFlowSources} from "@kestra-io/topology"
    import FlowChangeSummary from "../../flows/changeReview/FlowChangeSummary.vue"
    import AiMenuIcon from "../AiMenuIcon.vue"
    import DiffView from "./DiffView.vue"

    defineProps<{
        namespace: string
        id: string
        before: string
        after: string
    }>()
</script>

<style scoped>
    .apply-confirm {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
    }

    .apply-confirm-flow {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-3);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-surface);
    }

    .apply-confirm-icon {
        display: inline-flex;
        flex-shrink: 0;
        align-items: center;
        justify-content: center;
        width: var(--ks-spacing-7);
        height: var(--ks-spacing-7);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-info);
        color: var(--ks-text-info);
    }

    .apply-confirm-text {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        min-width: 0;
    }

    .apply-confirm-line {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .apply-confirm-id {
        font-family: var(--ks-font-family-mono);
        font-weight: var(--ks-font-weight-semibold);
        overflow-wrap: anywhere;
    }

    .apply-confirm-separator {
        margin: 0 var(--ks-spacing-1);
        color: var(--ks-text-secondary);
    }

    .apply-confirm-hint {
        align-self: flex-start;
        text-align: start;

        & strong {
            font-weight: var(--ks-font-weight-semibold);
            color: var(--ks-text-primary);
        }
    }

    .apply-confirm-diff {
        --diff-view-height: min(45vh, 26rem);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        overflow: hidden;
    }
</style>
