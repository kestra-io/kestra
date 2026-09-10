<template>
    <div class="diff-view" data-test="copilot-diff">
        <div class="diff-view-summary" data-test="copilot-diff-summary">
            <template v-if="hasChanges">
                <KsTag v-if="summary.added" type="success" size="small" :aria-label="addedLabel">
                    +{{ summary.added }}
                </KsTag>
                <KsTag v-if="summary.removed" type="error" size="small" :aria-label="removedLabel">
                    −{{ summary.removed }}
                </KsTag>
            </template>
            <KsText v-else size="small" class="diff-view-no-changes">{{ $t("ai.copilot.diff.noChanges") }}</KsText>
        </div>

        <div
            v-if="hasChanges"
            class="diff-view-lines"
            role="group"
            :aria-label="$t('ai.copilot.diff.ariaLabel')"
        >
            <template v-for="(entry, index) in hunks" :key="index">
                <div v-if="entry.kind === 'gap'" class="diff-view-line diff-view-line-gap">
                    <span class="diff-view-sign"> </span>
                    <span class="diff-view-code">{{ $t("ai.copilot.diff.hiddenLines", {count: entry.count}) }}</span>
                </div>
                <div v-else class="diff-view-line" :class="`diff-view-line-${entry.kind}`">
                    <span class="diff-view-sign">{{ sign(entry.kind) }}</span>
                    <span class="diff-view-code">{{ entry.text || " " }}</span>
                </div>
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {diffLines, summarizeDiff, collapseContext, type TextDiffLine} from "../../../utils/textDiff"

    const props = withDefaults(defineProps<{
        /** The current content ("before"). Empty when there's nothing to diff against (e.g. a brand-new
         *  flow) — every line of `newValue` then renders as an addition. */
        oldValue: string
        newValue: string
        /** Unchanged lines kept around each change before the rest collapses into a gap marker. */
        context?: number
    }>(), {context: 3})

    const {t} = useI18n()

    const entries = computed(() => diffLines(props.oldValue, props.newValue))
    const summary = computed(() => summarizeDiff(entries.value))
    const hasChanges = computed(() => summary.value.added > 0 || summary.value.removed > 0)
    const hunks = computed(() => collapseContext(entries.value, props.context))

    const addedLabel = computed(() => t("ai.copilot.diff.added", {count: summary.value.added}))
    const removedLabel = computed(() => t("ai.copilot.diff.removed", {count: summary.value.removed}))

    function sign(kind: TextDiffLine["kind"]): string {
        if (kind === "added") return "+"
        if (kind === "removed") return "−"
        return " "
    }
</script>

<style scoped>
    .diff-view {
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        overflow: hidden;
    }

    .diff-view-summary {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        background: var(--ks-bg-elevated);
    }

    .diff-view-no-changes {
        --kel-text-color: var(--ks-text-secondary);
    }

    .diff-view-lines {
        max-height: 16rem;
        overflow: auto;
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        background: var(--ks-bg-base);
    }

    .diff-view-line {
        display: flex;
        align-items: flex-start;
    }

    .diff-view-line-added {
        background: var(--ks-status-background-success);
    }

    .diff-view-line-added .diff-view-sign {
        color: var(--ks-status-success);
    }

    .diff-view-line-removed {
        background: var(--ks-status-background-failed);
    }

    .diff-view-line-removed .diff-view-sign {
        color: var(--ks-status-error);
    }

    .diff-view-line-gap {
        color: var(--ks-text-muted);
        font-style: italic;
    }

    .diff-view-sign {
        flex: 0 0 auto;
        width: var(--ks-spacing-4);
        text-align: center;
        color: var(--ks-text-muted);
        user-select: none;
    }

    .diff-view-code {
        flex: 1 1 auto;
        min-width: 0;
        white-space: pre-wrap;
        word-break: break-word;
        padding-inline-end: var(--ks-spacing-3);
        color: var(--ks-text-dim);
    }
</style>
