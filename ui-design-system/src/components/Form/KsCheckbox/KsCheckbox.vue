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
    import {ElCheckbox, provideGlobalConfig} from "element-plus"

    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const model = defineModel<any>()

    const props = withDefaults(defineProps<{
        value?: boolean | string | number
        label?: string | boolean | number | object
        disabled?: boolean
        checked?: boolean
        indeterminate?: boolean
    }>(), {
        value: undefined,
        label: undefined,
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
        --kel-checkbox-text-color: var(--ks-content-primary);
        --kel-checkbox-checked-text-color: var(--ks-content-primary);
        --kel-checkbox-font-size: var(--kel-font-size-base);

        html.dark & {
            --kel-checkbox-bg-color: var(--ks-background-input);
        }
    }
</style>