<template>
    <ElTimelineItem v-bind="({...filteredProps(), ...$attrs} as TimelineItemProps)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.dot" #dot>
            <slot name="dot" />
        </template>
    </ElTimelineItem>
</template>

<script setup lang="ts">
    import {ElTimelineItem, type TimelineItemProps} from "element-plus"
    import type {Component} from "vue"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        timestamp?: string;
        color?: string;
        type?: string;
        icon?: string | Component;
        size?: "normal" | "large";
        hideTimestamp?: boolean;
        placement?: "top" | "bottom";
        hollow?: boolean;
    }>()

    defineSlots<{
        default?(): unknown;
        dot?(): unknown;
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
@use "../../../assets/styles/el-ns";
@use "element-plus/theme-chalk/src/timeline-item";

.kel-timeline-item__node.is-hollow {
    background-color: var(--ks-bg-base);
}
</style>
