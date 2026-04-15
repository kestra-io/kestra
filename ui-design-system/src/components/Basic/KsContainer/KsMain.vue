<template>
    <ElMain v-bind="($attrs as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElMain>
</template>

<script setup lang="ts">
    import {ElMain, provideGlobalConfig} from "element-plus"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/main';
</style>
