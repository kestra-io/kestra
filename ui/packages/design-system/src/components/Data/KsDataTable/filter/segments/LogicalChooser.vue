<template>
    <div class="logical-chooser">
        <KsButton
            v-for="op in OPTIONS"
            :key="op"
            text
            size="small"
            class="logical-chooser-option"
            :class="{active: current === op}"
            @click="$emit('select', op)"
        >{{ op === "AND" ? $t("filter.and") : $t("filter.or") }}</KsButton>
    </div>
</template>

<script setup lang="ts">
    import type {LogicalOperator} from "../utils/filterTypes"

    defineProps<{
        current: LogicalOperator;
    }>()

    defineEmits<{
        select: [op: LogicalOperator];
    }>()

    const OPTIONS: LogicalOperator[] = ["AND", "OR"]
</script>

<style lang="scss" scoped>
.logical-chooser {
    display: flex;
    flex-direction: column;
    min-width: 4rem;
    padding: 0.25rem;
    gap: 0.125rem;
}

.logical-chooser-option {
    width: 100%;
    margin: 0 !important;
    justify-content: flex-start;

    &.active {
        font-weight: 600;
        color: var(--ks-text-link);
    }
}
</style>
