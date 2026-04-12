<script setup lang="ts">
    import {ElSwitch, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        modelValue?: boolean | string | number
        disabled?: boolean
        activeText?: string
        inactiveText?: string
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        activeActionIcon?: any
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        inactiveActionIcon?: any
        size?: "large" | "default" | "small"
        activeValue?: boolean | string | number
        inactiveValue?: boolean | string | number
    }>(), {
        activeValue: undefined,
        inactiveValue: undefined,
    })

    const emit = defineEmits<{
        "update:modelValue": [value: boolean | string | number]
        change: [value: boolean | string | number]
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<template>
    <el-switch
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
    />
</template>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/switch';

    .kel-switch {
        .kel-switch__label {
            color: var(--ks-content-primary);
        }
    }
</style>
