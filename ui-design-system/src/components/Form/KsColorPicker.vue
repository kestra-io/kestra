<template>
    <ElColorPicker
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
        @active-change="emit('activeChange', $event as string)"
    />
</template>

<script setup lang="ts">
    import {ElColorPicker, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        modelValue?: string
        showAlpha?: boolean
        disabled?: boolean
        size?: "large" | "default" | "small"
        predefine?: string[]
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        "update:modelValue": [value: string | null]
        change: [value: string | null]
        activeChange: [value: string]
    }>()
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/color-picker';
    @use 'element-plus/theme-chalk/src/color-picker-panel';
</style>
