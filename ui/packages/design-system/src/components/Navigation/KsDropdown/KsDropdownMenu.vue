<template>
    <ElDropdownMenu v-bind="$attrs" class="ks-dropdown-menu-padding">
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

    // Inject explicit structural padding onto the menu list container
    .ks-dropdown-menu-padding {
        padding-top: 8px !important;
        padding-bottom: 8px !important;
    }
</style>