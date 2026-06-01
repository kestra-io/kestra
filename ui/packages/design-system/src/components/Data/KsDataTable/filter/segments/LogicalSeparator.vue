<template>
    <KsPopover
        placement="bottom"
        trigger="click"
        :showArrow="false"
        :popperClass="'logical-popover'"
        :disabled="disabled"
    >
        <template #reference>
            <button
                type="button"
                class="logical-separator"
                :class="{inner, 'filters-hidden': hidden}"
                :disabled="disabled"
                :aria-label="logical"
            >{{ logical === "AND" ? $t("filter.and") : $t("filter.or") }}</button>
        </template>
        <LogicalChooser
            :current="logical"
            @select="(op: LogicalOperator) => $emit('change', op)"
        />
    </KsPopover>
</template>

<script setup lang="ts">
    import LogicalChooser from "./LogicalChooser.vue"
    import type {LogicalOperator} from "../utils/filterTypes"

    withDefaults(defineProps<{
        logical: LogicalOperator;
        disabled?: boolean;
        hidden?: boolean;
        inner?: boolean;
    }>(), {
        disabled: false,
        hidden: false,
        inner: false,
    })

    defineEmits<{
        change: [op: LogicalOperator];
    }>()
</script>

<style lang="scss" scoped>
.logical-separator {
    appearance: none;
    flex-shrink: 0;
    align-self: center;
    font-size: var(--ks-font-size-xs);
    font-weight: 600;
    color: var(--ks-text-dim);
    padding: 0.125rem 0.5rem;
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-sm);
    background-color: var(--ks-bg-elevated);
    letter-spacing: 0.05em;
    cursor: pointer;

    &:hover:not(:disabled) {
        color: var(--ks-text-primary);
        border-color: var(--ks-text-dim);
    }

    &:disabled {
        cursor: default;
        opacity: 0.6;
    }

    &.inner {
        padding: 0.0625rem 0.375rem;
        background-color: transparent;
    }

    &.filters-hidden {
        display: none;
    }
}
</style>
