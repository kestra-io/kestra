<script setup lang="ts">
    import {ElSplitterPanel, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        size?: string | number
        min?: string | number
        max?: string | number
        resizable?: boolean
        collapsible?: boolean
    }>(), {
        resizable: undefined,
    })

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-splitter-panel v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default><slot /></template>
    </el-splitter-panel>
</template>
