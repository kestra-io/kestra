<template>
    <ElSplitter v-bind="{...filteredProps(), ...$attrs}">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElSplitter>
</template>

<script setup lang="ts">
    import type {HTMLAttributes} from "vue"
    import {ElSplitter, type SplitterProps} from "element-plus"
    import {usePassedProps} from "../../../utils/filteredProps"
    import type {NativeAttributes} from "../../../utils/nativeAttributes"

    defineOptions({inheritAttrs: false})

    const props = defineProps<SplitterProps & /* @vue-ignore */ (Omit<NativeAttributes<HTMLAttributes>, keyof SplitterProps> & {onResizeEnd?: (index: number, sizes: number[]) => void})>()

    const filteredProps = usePassedProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/splitter';
</style>

<style>
    .kel-splitter-panel {
        background-color: var(--ks-splitter-panel-bg, transparent);
        display: var(--ks-splitter-panel-display, block);
        grid-template-rows: var(--ks-splitter-panel-rows, none);
    }

    .kel-splitter__horizontal > .kel-splitter-bar {
        width: var(--ks-splitter-bar-size, 0) !important;
    }

    .kel-splitter__vertical > .kel-splitter-bar {
        height: var(--ks-splitter-bar-size, 0) !important;
    }

    /* Element Plus inlines the dragger size; px, not rem, to stay matched to Monaco's verticalScrollbarSize (useKsEditor.ts). */

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
