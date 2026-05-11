<template>
    <ElInputNumber
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event, undefined)"
    />
</template>

<script setup lang="ts">
    import {ElInputNumber} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<number>()

    const props = withDefaults(defineProps<{
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
        min: undefined,
        max: undefined,
        step: undefined,
        precision: undefined,
        size: undefined,
        placeholder: undefined,
        controls: undefined,
        controlsPosition: undefined,
    })

    const emit = defineEmits<{
        change: [currentValue: number | undefined, oldValue: number | undefined]
    }>()

    const filteredProps = useFilteredProps(props)
</script>

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
                color: var(--ks-content-secondary);
        }
    }
</style>
