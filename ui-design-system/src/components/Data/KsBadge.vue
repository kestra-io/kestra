<script setup lang="ts">
    import {ElBadge, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        value?: string | number
        type?: "primary" | "success" | "warning" | "danger" | "info"
        max?: number
        isDot?: boolean
        hidden?: boolean
        showZero?: boolean
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-badge v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default><slot /></template>
    </el-badge>
</template>
