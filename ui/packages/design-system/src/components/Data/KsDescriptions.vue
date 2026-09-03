<template>
    <ElDescriptions v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.title" #title>
            <slot name="title" />
        </template>
        <template v-if="$slots.extra" #extra>
            <slot name="extra" />
        </template>
    </ElDescriptions>
</template>

<script setup lang="ts">
    import {ElDescriptions} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        border?: boolean
        column?: number
        direction?: "horizontal" | "vertical"
        size?: "large" | "default" | "small"
        title?: string
        extra?: string
    }>()

    defineSlots<{
        default?(): unknown
        title?(): unknown
        extra?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/descriptions';
    @use 'element-plus/theme-chalk/src/descriptions-item';
</style>
