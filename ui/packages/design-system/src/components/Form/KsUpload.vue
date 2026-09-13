<template>
    <ElUpload
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event, [])"
        @exceed="emit('exceed', $event, [])"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.tip" #tip>
            <slot name="tip" />
        </template>
    </ElUpload>
</template>

<script setup lang="ts">
    import {ElUpload} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        accept?: string
        autoUpload?: boolean
        drag?: boolean
        multiple?: boolean
        limit?: number
        action?: string
        showFileList?: boolean
        fileList?: any[]
    }>(), {
        accept: undefined,
        autoUpload: undefined,
        limit: undefined,
        action: undefined,
        showFileList: undefined,
        fileList: undefined,
    })

    const emit = defineEmits<{
        change: [file: any, fileList: any[]]
        exceed: [files: any[], fileList: any[]]
    }>()

    defineSlots<{
        default?(): unknown
        tip?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/upload';

    .kel-upload {
        &:focus {
            // Prevent purple color from propagating to child text nodes
            color: inherit;

            .kel-upload-dragger {
                border-color: var(--ks-border-focus);
            }
        }
    }

    .kel-upload-dragger {
        background-color: var(--ks-bg-input);
        border-color: var(--ks-border-default);
        border-radius: var(--ks-radius-base);

        &:hover {
            border-color: var(--ks-border-focus);
        }

        &.is-dragover {
            background-color: var(--ks-bg-hover);
            border-color: var(--ks-border-focus);
            border-width: 2px;
        }
    }
</style>
