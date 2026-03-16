<script setup lang="ts">
    import {ElTimeline, provideGlobalConfig} from "element-plus"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-timeline
        v-bind="({...$attrs} as any)"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-timeline>
</template>
