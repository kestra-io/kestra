<template>
    <ElCollapseItem
        v-bind="({...filteredProps(), ...$attrs} as CollapseItemProps)"
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
    import {ElCollapseItem, type CollapseItemProps} from "element-plus"
    import type {Component} from "vue"

    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        name?: string | number
        title?: string
        disabled?: boolean
        icon?: string | Component
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
