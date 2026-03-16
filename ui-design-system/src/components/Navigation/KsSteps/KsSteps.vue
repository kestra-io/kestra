<script setup lang="ts">
    import {ElSteps, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        active?: number
        direction?: "horizontal" | "vertical"
        space?: string | number
        finishStatus?: string
        processStatus?: string
        simple?: boolean
        alignCenter?: boolean
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-steps
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-steps>
</template>
