<script setup lang="ts">
    import {ElSkeleton, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        animated?: boolean
        count?: number
        loading?: boolean
        rows?: number
        throttle?: number
    }>(), {
        animated: undefined,
        loading: undefined,
    })

    defineSlots<{
        default?(): unknown
        template?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<template>
    <el-skeleton v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default><slot /></template>
        <template v-if="$slots.template" #template><slot name="template" /></template>
    </el-skeleton>
</template>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/skeleton';
    @use 'element-plus/theme-chalk/src/skeleton-item';
</style>
