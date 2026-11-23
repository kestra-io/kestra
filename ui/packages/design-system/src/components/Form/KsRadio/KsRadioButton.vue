<template>
    <ElRadioButton
        v-bind="({...filteredProps(), ...$attrs} as RadioButtonProps)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElRadioButton>
</template>

<script setup lang="ts">
    import {type RadioButtonProps} from "element-plus"
    import {ElRadioButton} from "../../../utils/elementPlusListeners"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        value?: string | number | boolean
        label?: string | number | boolean
        disabled?: boolean
    }>()

    const emit = defineEmits<{
        change: [event: Event]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/radio-button';
</style>

<style>
    .kel-radio-button {
        --kel-radio-button-checked-bg-color: var(--ks-radio-button-active-bg, var(--kel-color-primary));
        --kel-radio-button-checked-text-color: var(--ks-radio-button-active-color, var(--kel-color-white));
    }

    .kel-radio-button .kel-radio-button__inner {
        padding: var(--ks-radio-button-padding, 8px 15px);
        border-radius: var(--ks-radio-button-radius, 0);
        background: var(--ks-radio-button-bg, var(--kel-button-bg-color, var(--kel-fill-color-blank)));
    }

    .kel-radio-button.is-active .kel-radio-button__inner {
        border-color: var(--ks-radio-button-active-border-color, var(--kel-radio-button-checked-border-color, var(--kel-color-primary)));
    }

    .kel-radio-button:first-child .kel-radio-button__inner {
        border-radius: var(--ks-radio-button-radius, var(--kel-border-radius-base) 0 0 var(--kel-border-radius-base));
    }

    .kel-radio-button__original-radio:focus-visible + .kel-radio-button__inner {
        border-radius: var(--ks-radio-button-radius, var(--kel-border-radius-base));
    }

    .kel-radio-button:last-child .kel-radio-button__inner {
        border-radius: var(--ks-radio-button-radius, 0 var(--kel-border-radius-base) var(--kel-border-radius-base) 0);
    }

    .kel-radio-button:first-child:last-child .kel-radio-button__inner {
        border-radius: var(--ks-radio-button-radius, var(--kel-border-radius-base));
    }

    .kel-radio-button--small .kel-radio-button__inner {
        padding: var(--ks-radio-button-padding, 5px 11px);
    }

    .kel-radio-button--large .kel-radio-button__inner {
        padding: var(--ks-radio-button-padding, 12px 19px);
    }
</style>
