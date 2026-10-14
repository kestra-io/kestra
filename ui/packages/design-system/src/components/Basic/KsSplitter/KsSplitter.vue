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

    // Element Plus inlines the dragger size; px, not rem, to stay matched to Monaco's verticalScrollbarSize (useKsEditor.ts).
    .kel-splitter-bar__dragger-horizontal {
        width: 8px !important;
    }

    .kel-splitter-bar__dragger-vertical {
        height: 8px !important;
    }

    @media (pointer: coarse) {
        .kel-splitter-bar__dragger-horizontal {
            width: 16px !important;
        }

        .kel-splitter-bar__dragger-vertical {
            height: 16px !important;
        }
    }
</style>
