<template>
    <ElBadge v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElBadge>
</template>

<script setup lang="ts">
    import {ElBadge} from "element-plus"

    import {useFilteredProps} from "../../utils/filteredProps"

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

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/badge';
</style>
