<template>
    <ElContainer v-bind="$attrs">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElContainer>
</template>

<script setup lang="ts">
    import {ElContainer} from "element-plus"

    defineOptions({inheritAttrs: false})

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/container';
</style>
