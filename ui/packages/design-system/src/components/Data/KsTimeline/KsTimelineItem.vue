<template>
    <ElTimelineItem
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.dot" #dot>
            <slot name="dot" />
        </template>
    </ElTimelineItem>
</template>

<script setup lang="ts">
    import {ElTimelineItem} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        timestamp?: string
        color?: string
        type?: string
        icon?: any
        size?: "normal" | "large"
        hideTimestamp?: boolean
        placement?: "top" | "bottom"
    }>()

    defineSlots<{
        default?(): unknown
        dot?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/timeline-item';
</style>
