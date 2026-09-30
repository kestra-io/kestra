<template>
    <KsTooltip v-if="issues.length" :persistent="false">
        <template #content>
            <div class="task-validation-tooltip">
                <div class="task-validation-tooltip-head">
                    <AlertCircle :size="14" />
                    <span>{{ $t("error detected") }}</span>
                </div>
                <ul class="task-validation-tooltip-list">
                    <li v-for="issue in issues" :key="issue">{{ issue }}</li>
                </ul>
            </div>
        </template>
        <span
            class="task-validation-badge"
            data-test="topology-task-validation-badge"
            role="img"
            tabindex="0"
            :aria-label="$t('flow_editor_stats.errors.label', {count: issues.length})"
        >
            <AlertCircle :size="14" />
            <span v-if="issues.length > 1" class="task-validation-badge-count">{{ issues.length }}</span>
        </span>
    </KsTooltip>
</template>

<script setup lang="ts">
    import {KsTooltip} from "@kestra-io/design-system"
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue"

    defineOptions({name: "ValidationBadge"})

    defineProps<{issues: string[]}>()
</script>

<style lang="scss" scoped>
    .task-validation-badge {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        flex-shrink: 0;
        padding: 0 var(--ks-spacing-1);
        height: 1.125rem;
        border-radius: var(--ks-radius-sm);
        background: var(--ks-bg-error);
        color: var(--ks-text-error);
        cursor: help;
    }

    .task-validation-badge-count {
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        line-height: 1;
        font-variant-numeric: tabular-nums;
    }

    .task-validation-tooltip {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        max-width: 22rem;
    }

    .task-validation-tooltip-head {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        color: var(--ks-text-error);
        font-weight: 600;
        font-size: var(--ks-font-size-sm);
    }

    .task-validation-tooltip-list {
        margin: 0;
        padding-left: var(--ks-spacing-4);
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
        font-family: var(--ks-font-family-mono);
    }
</style>
