<script setup lang="ts">
    import {ElCollapseItem, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        name?: string | number
        title?: string
        disabled?: boolean
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        icon?: any
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
        title?(): unknown
    }>()
</script>

<template>
    <el-collapse-item
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default><slot /></template>
        <template v-if="$slots.title" #title><slot name="title" /></template>
    </el-collapse-item>
</template>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/collapse-item';
</style>
