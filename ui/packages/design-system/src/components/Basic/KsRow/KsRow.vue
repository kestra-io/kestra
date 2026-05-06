<template>
    <ElRow v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElRow>
</template>

<script setup lang="ts">
    import {ElRow} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        gutter?: number
        justify?: "start" | "end" | "center" | "space-around" | "space-between" | "space-evenly"
        align?: "top" | "middle" | "bottom"
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/row';
</style>
