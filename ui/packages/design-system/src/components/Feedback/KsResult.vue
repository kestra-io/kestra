<template>
    <ElResult v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.icon" #icon>
            <slot name="icon" />
        </template>
        <template v-if="$slots.title" #title>
            <slot name="title" />
        </template>
        <template v-if="$slots['sub-title']" #sub-title>
            <slot name="sub-title" />
        </template>
        <template v-if="$slots.extra" #extra>
            <slot name="extra" />
        </template>
    </ElResult>
</template>

<script setup lang="ts">
    import {ElResult} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        title?: string
        subTitle?: string
        icon?: "primary" | "success" | "warning" | "info" | "error"
    }>()

    defineSlots<{
        default?(): unknown
        icon?(): unknown
        title?(): unknown
        "sub-title"?(): unknown
        extra?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/result';
</style>
