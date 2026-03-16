<script setup lang="ts">
    import {ElUpload, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        accept?: string
        autoUpload?: boolean
        drag?: boolean
        multiple?: boolean
        limit?: number
        action?: string
        showFileList?: boolean
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        fileList?: any[]
    }>(), {
        autoUpload: undefined,
        showFileList: undefined,
    })

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        change: [file: any, fileList: any[]]
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        exceed: [files: any[], fileList: any[]]
    }>()

    defineSlots<{
        default?(): unknown
        tip?(): unknown
    }>()
</script>

<template>
    <el-upload
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event, [])"
        @exceed="emit('exceed', $event, [])"
    >
        <template v-if="$slots.default" #default><slot /></template>
        <template v-if="$slots.tip" #tip><slot name="tip" /></template>
    </el-upload>
</template>
