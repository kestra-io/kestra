<template>
    <ElCheckboxButton
        v-bind="({...filteredProps(), ...$attrs} as CheckboxProps)"
        @change="emit('change', $event)"
    >
        <template v-if="ariaLabel" #default>
            <span class="ks-checkbox-button-label">{{ ariaLabel }}</span>
            <span aria-hidden="true"><slot /></span>
        </template>
        <template v-else-if="$slots.default" #default>
            <slot />
        </template>
    </ElCheckboxButton>
</template>

<script setup lang="ts">
    import {ElCheckboxButton, type CheckboxProps, type CheckboxValueType} from "element-plus"

    import type {HTMLAttributes} from "vue"
    import {useFilteredProps} from "../../../utils/filteredProps"
    import type {NativeAttributes} from "../../../utils/nativeAttributes"

    defineOptions({inheritAttrs: false})

    export interface KsCheckboxButtonProps {
        value?: boolean | string | number
        disabled?: boolean
        checked?: boolean
        /** Element Plus never renders it on the button's input, so it is rendered as hidden label text. */
        ariaLabel?: string
    }

    const props = defineProps<KsCheckboxButtonProps & /* @vue-ignore */ Omit<NativeAttributes<CheckboxProps & HTMLAttributes>, keyof KsCheckboxButtonProps | "id">>()

    const emit = defineEmits<{
        change: [value: CheckboxValueType]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/checkbox-button';
</style>

<style>
    .ks-checkbox-button-label {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip-path: inset(50%);
        white-space: nowrap;
    }

    .kel-checkbox-button:not(.is-checked):not(.is-disabled):hover .kel-checkbox-button__inner {
        color: var(--ks-text-link);
        border-color: var(--ks-text-link);
    }

    .kel-checkbox-button__original:focus-visible + .kel-checkbox-button__inner {
        outline: 2px solid var(--ks-border-focus);
        outline-offset: -2px;
        position: relative;
        z-index: 1;
    }
</style>
