<template>
    <ElSplitter v-bind="$attrs">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElSplitter>
</template>

<script setup lang="ts">
    import {ElSplitter} from "element-plus"

    defineOptions({inheritAttrs: false})

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/splitter';

    // Sizes are pinned to px, not the rem spacing scale: they are measured
    // against Monaco's verticalScrollbarSize, a device-independent px value.
    // Element Plus inlines the dragger size, so !important is the only override point.
    .kel-splitter-bar__dragger-horizontal {
        width: 8px !important;
    }

    .kel-splitter-bar__dragger-vertical {
        height: 8px !important;
    }

    // Coarse pointers keep the original 16px: the editor is panned directly on
    // touch, so conceding the scrollbar strip costs less than a thin drag target.
    @media (pointer: coarse) {
        .kel-splitter-bar__dragger-horizontal {
            width: 16px !important;
        }

        .kel-splitter-bar__dragger-vertical {
            height: 16px !important;
        }
    }
</style>
