<template>
    <ElCol v-bind="{...filteredProps(), ...$attrs}">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElCol>
</template>

<script setup lang="ts">
    import {ElCol, type ColSize} from "element-plus"

    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        span?: number
        xs?: ColSize
        sm?: ColSize
        md?: ColSize
        lg?: ColSize
        xl?: ColSize
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/col';
</style>
