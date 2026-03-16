<script setup lang="ts">
    import {ElSplitter, provideGlobalConfig} from "element-plus"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-splitter v-bind="($attrs as any)">
        <template v-if="$slots.default" #default><slot /></template>
    </el-splitter>
</template>
