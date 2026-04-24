<template>
    <ElDropdownMenu v-bind="$attrs">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElDropdownMenu>
</template>

<script setup lang="ts">
    import {ElDropdownMenu} from "element-plus"

    defineOptions({inheritAttrs: false})

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/dropdown-menu';
</style>
