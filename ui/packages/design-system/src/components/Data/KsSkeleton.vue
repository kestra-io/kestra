<template>
    <ElSkeleton v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.template" #template>
            <slot name="template" />
        </template>
    </ElSkeleton>
</template>

<script setup lang="ts">
    import {ElSkeleton} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        animated?: boolean
        count?: number
        loading?: boolean
        rows?: number
        throttle?: number
    }>(), {
        animated: undefined,
        count: undefined,
        loading: undefined,
        rows: undefined,
        throttle: undefined,
    })

    defineSlots<{
        default?(): unknown
        template?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/skeleton';
    @use 'element-plus/theme-chalk/src/skeleton-item';
</style>
