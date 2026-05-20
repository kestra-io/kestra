<template>
    <ElRadioGroup
        v-model="model"
        class="kel-tabs-toggle"
        :disabled="disabled"
        v-bind="$attrs"
        :aria-label="ariaLabel"
        @change="emit('change', $event)"
    >
        <slot />
    </ElRadioGroup>
</template>

<script setup lang="ts">
    import {ElRadioGroup} from "element-plus"

    defineOptions({inheritAttrs: false})

    const model = defineModel<string | number | boolean>()

    defineProps<{
        disabled?: boolean
        ariaLabel?: string
    }>()

    const emit = defineEmits<{
        change: [value: string | number | boolean | undefined]
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/radio-group';
    @use 'element-plus/theme-chalk/src/radio-button';

    .kel-tabs-toggle {
        display: inline-flex;
        align-items: center;
        // Flex-column parents (e.g. .form-field) stretch children on the
        // cross-axis — pin width to content so the container hugs its pills.
        align-self: flex-start;
        width: fit-content;
        gap: var(--ks-spacing-1);
        height: 36px;
        padding: 4px;
        border-bottom: var(--ks-border-width-thin) solid transparent;
        border-radius: 8px;
        background: var(--ks-tabs-bg);

        .kel-radio-button {
            display: inline-flex;
            height: 100%;
            margin: 0;
        }

        // EP draws segmented borders via `outline` on the inner, and the
        // `:first-child` rule below applies asymmetric border-radius — strip
        // both so each pill stands alone.
        .kel-radio-button .kel-radio-button__inner,
        .kel-radio-button:first-child .kel-radio-button__inner {
            display: inline-flex;
            align-items: center;
            gap: 0.25rem;
            height: 100%;
            padding: var(--ks-spacing-1) var(--ks-spacing-2);
            border: var(--ks-border-width-thin) solid transparent;
            border-radius: var(--ks-radius-sm);
            background: transparent;
            outline: none;
            box-shadow: none;
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-xs);
            font-weight: var(--ks-font-weight-regular);
            line-height: 1;
            transition:
                background-color 0.15s ease,
                color 0.15s ease,
                border-color 0.15s ease;
        }

        .kel-radio-button .kel-radio-button__inner:hover {
            color: var(--ks-text-primary);
            background: transparent;
        }

        // Active selector mirrors EP's own — equal specificity, declared after,
        // so cascade order wins and the box-tab pill style replaces EP's solid
        // primary fill.
        .kel-radio-button.is-active .kel-radio-button__original-radio:not(:disabled) + .kel-radio-button__inner {
            background: var(--ks-btn-secondary-bg-active);
            border-color: var(--ks-btn-secondary-border-active);
            color: var(--ks-text-primary);
            box-shadow: none;
            outline: none;
        }

        .kel-radio-button.is-active .kel-radio-button__original-radio:focus-visible + .kel-radio-button__inner {
            outline: 2px solid var(--ks-border-active);
            outline-offset: 2px;
            border-left: var(--ks-border-width-thin) solid var(--ks-btn-secondary-border-active);
        }

        .kel-radio-button.is-disabled .kel-radio-button__inner {
            color: var(--ks-text-inactive);
            cursor: not-allowed;
        }
    }
</style>
