<template>
    <div class="selectable-tile" :class="{selected, disabled, 'has-config': hasConfig}">
        <button
            type="button"
            class="tile-button"
            :class="layout"
            :role="role"
            :aria-checked="selected"
            :aria-disabled="disabled || undefined"
            :aria-label="ariaLabel"
            :disabled="disabled"
            :tabindex="disabled ? -1 : 0"
            @click="onSelect"
        >
            <slot />
        </button>

        <div v-if="hasConfig" class="tile-config" @click.stop>
            <slot name="config" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, useSlots} from "vue"

    const props = withDefaults(defineProps<{
        role?: "radio" | "checkbox"
        selected?: boolean
        disabled?: boolean
        ariaLabel?: string
        layout?: "row" | "column"
    }>(), {
        role: "radio",
        selected: false,
        disabled: false,
        ariaLabel: undefined,
        layout: "row",
    })

    const emit = defineEmits<{
        select: []
    }>()

    const slots = useSlots()
    const hasConfig = computed(() => Boolean(slots.config) && props.selected)

    const onSelect = () => {
        if (!props.disabled) emit("select")
    }
</script>

<style scoped lang="scss">
    .selectable-tile {
        display: flex;
        flex-direction: column;
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background-color: var(--ks-bg-surface);
        transition: border-color var(--ks-duration-fast) var(--ks-ease-standard),
            background-color var(--ks-duration-fast) var(--ks-ease-standard);

        &.selected {
            border-color: var(--ks-border-focus);
            background-color: var(--ks-bg-tag-active);
        }

        &:has(.tile-button:hover:not(:disabled)) {
            border-color: var(--ks-border-strong);
            background-color: var(--ks-bg-hover);
        }

        &.selected:has(.tile-button:hover:not(:disabled)) {
            background-color: var(--ks-bg-tag-active);
        }

        &:has(.tile-button:focus-visible) {
            outline: var(--ks-border-width-base) solid var(--ks-border-focus);
            outline-offset: var(--ks-spacing-px);
        }

        &.disabled {
            opacity: 0.5;
        }
    }

    .tile-button {
        display: flex;
        gap: var(--ks-spacing-2);
        width: 100%;
        padding: var(--ks-spacing-3);
        border: none;
        background: none;
        color: inherit;
        font: inherit;
        text-align: left;
        cursor: pointer;

        &.row {
            align-items: center;
        }

        &.column {
            flex-direction: column;
            align-items: stretch;
        }

        &:disabled {
            cursor: not-allowed;
        }

        &:focus-visible {
            outline: none;
        }
    }

    .tile-config {
        padding: 0 var(--ks-spacing-3) var(--ks-spacing-3);
    }
</style>
