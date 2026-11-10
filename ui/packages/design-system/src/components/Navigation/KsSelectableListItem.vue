<template>
    <button
        type="button"
        class="ks-selectable-list-item"
        :class="{'is-selected': selected, 'is-inset': inset}"
        :aria-current="selected ? 'true' : undefined"
        :disabled="disabled"
    >
        <span v-if="$slots.leading" class="ks-selectable-list-item__leading">
            <slot name="leading" />
        </span>
        <span class="ks-selectable-list-item__content">
            <span class="ks-selectable-list-item__title"><slot>{{ title }}</slot></span>
            <span v-if="subtitle" class="ks-selectable-list-item__subtitle">{{ subtitle }}</span>
        </span>
    </button>
</template>

<script setup lang="ts">
    defineProps<{
        title?: string
        subtitle?: string
        selected?: boolean
        disabled?: boolean
        inset?: boolean
    }>()

    defineSlots<{
        default?(): unknown
        leading?(): unknown
    }>()
</script>

<style scoped>
    .ks-selectable-list-item {
        display: flex;
        width: 100%;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        font: inherit;
        color: var(--ks-text-primary);
        text-align: left;
        background: transparent;
        border: 1px solid transparent;
        border-radius: var(--ks-radius-base);
        cursor: pointer;

        &:hover:not(:disabled) {
            background: var(--ks-bg-hover);
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-focus);
            outline-offset: 2px;
        }

        &.is-selected {
            background: var(--ks-bg-active);
            border-color: var(--ks-border-focus);
        }

        &.is-inset {
            margin-left: var(--ks-spacing-4);
            width: calc(100% - var(--ks-spacing-4));
        }

        &:disabled {
            color: var(--ks-text-inactive);
            cursor: not-allowed;
        }
    }

    .ks-selectable-list-item__leading {
        display: inline-flex;
        flex: 0 0 auto;
        color: var(--ks-icon-muted);
    }

    .ks-selectable-list-item.is-selected .ks-selectable-list-item__leading {
        color: var(--ks-icon-active);
    }

    .ks-selectable-list-item__content {
        display: flex;
        min-width: 0;
        flex-direction: column;
        align-items: flex-start;
    }

    .ks-selectable-list-item__title,
    .ks-selectable-list-item__subtitle {
        width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .ks-selectable-list-item__subtitle {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }
</style>
