<script setup lang="ts">
    import {ElDropdownMenu, provideGlobalConfig} from "element-plus"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-dropdown-menu
        v-bind="({...$attrs} as any)"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-dropdown-menu>
</template>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/dropdown-menu';
</style>
