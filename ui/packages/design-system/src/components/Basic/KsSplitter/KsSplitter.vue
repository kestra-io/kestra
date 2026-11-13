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

    const props = defineProps<SplitterProps & /* @vue-ignore */ Omit<NativeAttributes<HTMLAttributes>, keyof SplitterProps>>()

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
