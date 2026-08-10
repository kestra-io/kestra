<template>
    <ElCheckboxButton
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElCheckboxButton>
</template>

<script setup lang="ts">
    import {ElCheckboxButton} from "element-plus"

    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        value?: boolean | string | number
        disabled?: boolean
        checked?: boolean
    }>()

    const emit = defineEmits<{
        change: [value: any]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/checkbox-button';

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
