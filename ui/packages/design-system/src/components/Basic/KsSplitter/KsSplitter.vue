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

    // Element Plus inlines a 16px hit zone on the dragger, centred on a 0px bar,
    // so it overlays the 8px of each neighbouring panel that sit along the bar.
    // Monaco's vertical scrollbar is 10px wide and lives exactly there: grabbing
    // it resized the panel instead of scrolling. Halve the zone so 4px overlap.
    .kel-splitter-bar__dragger-horizontal {
        width: 8px !important;
    }

    .kel-splitter-bar__dragger-vertical {
        height: 8px !important;
    }
</style>
