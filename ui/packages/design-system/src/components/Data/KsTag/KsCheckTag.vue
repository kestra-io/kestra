<template>
    <ElCheckTag
        v-bind="({...filteredProps(), ...$attrs} as any)"
        :class="[
            `kel-check-tag--${size}`,
            {'kel-check-tag--pill': pill},
        ]"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.icon || $slots.default" #default>
            <span v-if="$slots.icon" class="kel-check-tag__icon">
                <slot name="icon" />
            </span>
            <slot />
        </template>
    </ElCheckTag>
</template>

<script setup lang="ts">
    import {ElCheckTag} from "element-plus"

    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        checked?: boolean
        disabled?: boolean
        size?: "large" | "default" | "small"
        pill?: boolean
    }>(), {
        size: "small",
        pill: false,
    })

    const emit = defineEmits<{
        change: [checked: boolean]
    }>()

    defineSlots<{
        default?(): unknown
        icon?(): unknown
    }>()

    const filteredProps = useFilteredProps(props, ["size", "pill"])
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/check-tag';

    .kel-check-tag {
        &--small {
            --kel-font-size-base: var(--ks-font-size-xs);
            padding: 3px 8px;
        }

        &--default {
            --kel-font-size-base: var(--ks-font-size-base);
            padding: 7px 15px;
        }

        &--large {
            --kel-font-size-base: var(--ks-font-size-md);
            padding: 9px 18px;
        }

        &__icon {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
        }
    }

    .kel-check-tag.kel-check-tag--pill {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-4);
        border-radius: 999px;
        background-color: var(--ks-bg-surface);
        color: var(--ks-text-secondary);
        border: 1px solid transparent;

        &:hover:not(.is-disabled) {
            background-color: var(--ks-bg-active);
            border-color: var(--ks-border-focus);
        }

        &.is-checked {
            background-color: var(--ks-bg-active);
            border: 1px solid var(--ks-border-focus);
            color: var(--ks-text-primary);
        }

        &.is-disabled {
            cursor: not-allowed;
            opacity: 0.5;
        }
    }
</style>
