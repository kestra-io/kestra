<script setup lang="ts">
    import {ElInputNumber, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        modelValue?: number
        min?: number
        max?: number
        step?: number
        stepStrictly?: boolean
        precision?: number
        disabled?: boolean
        size?: "large" | "default" | "small"
        placeholder?: string
        controls?: boolean
        controlsPosition?: "" | "right"
    }>(), {
        controls: undefined,
    })

    const emit = defineEmits<{
        "update:modelValue": [value: number | undefined]
        change: [currentValue: number | undefined, oldValue: number | undefined]
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<template>
    <el-input-number
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event, undefined)"
    />
</template>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/input-number';

    .kel-input-number {
        background-color: var(--ks-background-body);
        width: 100%;

        .kel-input-number__increase, .kel-input-number__decrease {
            background: var(--ks-background-card);
        }

        .kel-input-number__increase:hover, .kel-input-number__decrease:hover {
            html.dark & {
                color: var(--ks-gray-700);
            }
        }
    }
</style>
