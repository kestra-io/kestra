<script setup lang="ts">
    import {ElButtonGroup, provideGlobalConfig} from "element-plus"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    defineProps<{
        size?: "small" | "default" | "large" | ""
        direction?: "horizontal" | "vertical"
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-button-group v-bind="({...$props, ...$attrs} as any)">
        <template v-if="$slots.default" #default><slot /></template>
    </el-button-group>
</template>
