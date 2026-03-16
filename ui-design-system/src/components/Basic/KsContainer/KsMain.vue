<script setup lang="ts">
    import {ElMain, provideGlobalConfig} from "element-plus"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-main v-bind="($attrs as any)">
        <template v-if="$slots.default" #default><slot /></template>
    </el-main>
</template>
