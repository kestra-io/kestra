<script setup lang="ts">
    import {ElProgress, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        percentage?: number
        type?: "line" | "circle" | "dashboard"
        strokeWidth?: number
        color?: string | object | ((pct: number) => string)
        showText?: boolean
        status?: "" | "success" | "exception" | "warning"
        striped?: boolean
        stripedFlow?: boolean
    }>(), {
        showText: undefined,
    })

    const filteredProps = useFilteredProps(props)
</script>

<template>
    <el-progress v-bind="({...filteredProps(), ...$attrs} as any)" />
</template>
