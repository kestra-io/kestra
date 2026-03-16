<script setup lang="ts">
    import {ElText, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        type?: "" | "primary" | "success" | "warning" | "danger" | "info"
        size?: "small" | "default" | "large"
        truncated?: boolean
        lineClamp?: string | number
        tag?: string
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-text v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default><slot /></template>
    </el-text>
</template>
