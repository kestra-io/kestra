<script setup lang="ts">
    import {ElTimelineItem, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        timestamp?: string
        color?: string
        type?: string
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        icon?: any
        size?: "normal" | "large"
        hideTimestamp?: boolean
        placement?: "top" | "bottom"
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
        dot?(): unknown
    }>()
</script>

<template>
    <el-timeline-item
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default><slot /></template>
        <template v-if="$slots.dot" #dot><slot name="dot" /></template>
    </el-timeline-item>
</template>
