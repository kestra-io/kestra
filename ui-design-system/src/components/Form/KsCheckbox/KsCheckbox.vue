<script setup lang="ts">
    import {ElCheckbox, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        modelValue?: boolean | string | number | any[]
        value?: boolean | string | number
        label?: string | boolean | number | object
        disabled?: boolean
        checked?: boolean
        indeterminate?: boolean
        trueValue?: boolean | string | number
        falseValue?: boolean | string | number
    }>(), {
        trueValue: undefined,
        falseValue: undefined,
    })

    const emit = defineEmits<{
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        "update:modelValue": [value: any]
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        change: [value: any]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<template>
    <el-checkbox
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-checkbox>
</template>

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