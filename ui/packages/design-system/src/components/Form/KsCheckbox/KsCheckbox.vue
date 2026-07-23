<template>
    <ElCheckbox
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElCheckbox>
</template>

<script setup lang="ts">
    import {ElCheckbox} from "element-plus"

    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<any>()

    const props = withDefaults(defineProps<{
        value?: boolean | string | number
        disabled?: boolean
        checked?: boolean
        indeterminate?: boolean
    }>(), {
        value: undefined,
    })

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
    @use 'element-plus/theme-chalk/src/checkbox';

    .kel-checkbox {
        --kel-checkbox-text-color: var(--ks-text-primary);
        --kel-checkbox-checked-text-color: var(--ks-text-primary);
        --kel-checkbox-font-size: var(--ks-font-size-base);
        --kel-checkbox-font-weight: var(--kbs-body-font-weight);
        --kel-checkbox-input-width: 1rem;
        --kel-checkbox-input-height: 1rem;
        --kel-checkbox-border-radius: 0.3rem;
        --kel-checkbox-input-border: 1px solid var(--ks-border-strong);
        --kel-checkbox-bg-color: transparent;
    }
</style>
