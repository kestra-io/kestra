<template>
    <ElTimeline v-bind="$attrs">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElTimeline>
</template>

<script setup lang="ts">
    import {ElTimeline} from "element-plus"

    defineOptions({inheritAttrs: false})

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/timeline';
</style>
