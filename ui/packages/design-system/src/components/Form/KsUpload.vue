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
</style>
