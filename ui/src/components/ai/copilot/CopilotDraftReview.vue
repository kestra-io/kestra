<template>
    <div class="copilot-draft-review" data-test="copilot-draft-review">
        <div class="review-flow">
            <KsIcon class="review-flow-icon">
                <AiMenuIcon :size="20" />
            </KsIcon>
            <div class="review-flow-text">
                <div class="review-flow-line">
                    <KsText class="review-flow-id">
                        {{ namespace }}<span class="review-flow-separator">/</span>{{ flowId }}
                    </KsText>
                    <KsTag size="small" type="info">{{ $t("draft") }}</KsTag>
                </div>
                <KsText
                    size="small"
                    type="secondary"
                    class="review-flow-hint"
                    data-test="copilot-draft-review-hint"
                >
                    <strong>{{ $t("ai.copilot.draft.applyDraftTitle") }}</strong>
                    {{ $t("ai.copilot.draft.applyDraftHint") }}
                </KsText>
            </div>
        </div>

        <FlowChangeReview
            :before="before"
            :after="after"
            rejectable
            @update:effective="emit('update:effective', $event)"
        />
    </div>
</template>

<script setup lang="ts">
    import FlowChangeReview from "../../flows/changeReview/FlowChangeReview.vue"
    import AiMenuIcon from "../AiMenuIcon.vue"

    defineProps<{
        namespace: string
        flowId: string
        before?: string
        after: string
    }>()

    const emit = defineEmits<{"update:effective": [source: string | undefined]}>()
</script>

<style scoped>
    .copilot-draft-review {
        --flow-change-review-height: 60vh;
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
    }

    .review-flow {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-3);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-surface);
    }

    .review-flow-icon {
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

    .review-flow-text {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        min-width: 0;
    }

    .review-flow-line {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .review-flow-id {
        font-family: var(--ks-font-family-mono);
        font-weight: var(--ks-font-weight-semibold);
        overflow-wrap: anywhere;
    }

    .review-flow-separator {
        margin: 0 var(--ks-spacing-1);
        color: var(--ks-text-secondary);
    }

    .review-flow-hint {
        align-self: flex-start;
        text-align: start;

        & strong {
            font-weight: var(--ks-font-weight-semibold);
            color: var(--ks-text-primary);
        }
    }
</style>
