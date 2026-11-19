<template>
    <span
        v-if="label"
        class="diff-badge"
        :class="`diff-badge--${status}`"
        data-test="topology-diff-badge"
    >
        {{ label }}
    </span>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import type {TaskDiffStatus} from "../utils/flowDiff"

    defineOptions({name: "DiffBadge"})

    const props = defineProps<{status?: TaskDiffStatus}>()

    const {t} = useI18n()

    const label = computed(() => (props.status && props.status !== "unchanged"
        ? t(`topology-graph.diff.${props.status}`)
        : undefined))
</script>

<style scoped>
    .diff-badge {
        display: inline-flex;
        align-items: center;
        flex-shrink: 0;
        height: 1.125rem;
        padding: 0 var(--ks-spacing-2);
        border: 1px solid;
        border-radius: var(--ks-radius-sm);
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        line-height: 1;
        white-space: nowrap;
        background: linear-gradient(var(--diff-badge-tint), var(--diff-badge-tint)), var(--ks-bg-surface);
    }

    .diff-badge--added {
        --diff-badge-tint: var(--ks-bg-success);
        color: var(--ks-text-success);
        border-color: var(--ks-border-success);
    }

    .diff-badge--removed {
        --diff-badge-tint: var(--ks-bg-error);
        color: var(--ks-text-error);
        border-color: var(--ks-border-error);
    }

    .diff-badge--changed {
        --diff-badge-tint: var(--ks-bg-warning);
        color: var(--ks-text-warning);
        border-color: var(--ks-border-warning);
    }

    .diff-badge--renamed,
    .diff-badge--moved {
        --diff-badge-tint: var(--ks-bg-info);
        color: var(--ks-text-info);
        border-color: var(--ks-border-info);
    }
</style>
