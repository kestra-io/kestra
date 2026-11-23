<template>
    <div class="change-summary" data-test="flow-change-summary">
        <KsTag
            v-for="kind in KINDS.filter(({name}) => diff.counts[name])"
            :key="kind.name"
            size="small"
            :type="kind.tone"
        >
            {{ $t(`flow_change_review.status.${kind.name}`) }}
            <span class="change-summary-count">{{ diff.counts[kind.name] }}</span>
        </KsTag>
        <KsText v-if="diff.counts.unchanged" size="small" type="secondary">
            {{ $t("flow_change_review.unchanged", {count: diff.counts.unchanged}) }}
        </KsText>
    </div>
</template>

<script setup lang="ts">
    import type {FlowDiff} from "@kestra-io/topology"

    defineProps<{diff: FlowDiff}>()

    const KINDS = [
        {name: "added", tone: "success"},
        {name: "removed", tone: "danger"},
        {name: "changed", tone: "warning"},
        {name: "renamed", tone: "info"},
        {name: "moved", tone: "info"},
    ] as const
</script>

<style scoped>
    .change-summary {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .change-summary-count {
        font-weight: var(--ks-font-weight-semibold);
    }
</style>
