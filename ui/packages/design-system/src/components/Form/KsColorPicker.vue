<template>
    <ElColorPicker
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
        @active-change="emit('activeChange', $event as string)"
    />
</template>

<script setup lang="ts">
    import {ElColorPicker} from "element-plus"

    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<string | null>()

    const props = defineProps<{
        showAlpha?: boolean
        disabled?: boolean
        size?: "large" | "default" | "small"
        predefine?: string[]
    }>()

    const emit = defineEmits<{
        change: [value: string | null]
        activeChange: [value: string]
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/color-picker';
    @use 'element-plus/theme-chalk/src/color-picker-panel';
</style>
