<template>
    <ElCollapseItem
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.title" #title="p">
            <slot name="title" v-bind="p" />
        </template>
        <template v-if="$slots.icon" #icon="p">
            <slot name="icon" v-bind="p" />
        </template>
    </ElCollapseItem>
</template>

<script setup lang="ts">
    import {ElCollapseItem} from "element-plus"

    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        name?: string | number
        title?: string
        disabled?: boolean
        icon?: any
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
        title?(props: { isActive?: boolean }): unknown
        icon?(props: { isActive?: boolean }): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/collapse-item';
</style>
