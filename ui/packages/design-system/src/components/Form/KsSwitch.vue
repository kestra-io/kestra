<template>
    <ElSwitch
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    />
</template>

<script setup lang="ts">
    import {ElSwitch} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<boolean | string | number>()

    const props = withDefaults(defineProps<{
        disabled?: boolean
        activeText?: string
        inactiveText?: string
        activeActionIcon?: any
        inactiveActionIcon?: any
        size?: "large" | "default" | "small"
        activeValue?: boolean | string | number
        inactiveValue?: boolean | string | number
    }>(), {
        activeText: undefined,
        inactiveText: undefined,
        activeActionIcon: undefined,
        inactiveActionIcon: undefined,
        size: undefined,
        activeValue: undefined,
        inactiveValue: undefined,
    })

    const emit = defineEmits<{
        change: [value: boolean | string | number]
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/switch';

    .kel-switch {
        .kel-switch__label {
            color: var(--ks-text-primary);
        }
    }
</style>
