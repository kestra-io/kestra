<template>
    <ElDivider v-bind="{...filteredProps(), ...$attrs}">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElDivider>
</template>

<script setup lang="ts">
    import {ElDivider, type DividerProps} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<DividerProps>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/divider';
</style>
