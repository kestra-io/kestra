<template>
    <ElSplitter v-bind="$attrs">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElSplitter>
</template>

<script setup lang="ts">
    import {ElSplitter, provideGlobalConfig} from "element-plus"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/splitter';
</style>
