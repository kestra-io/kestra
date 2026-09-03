<template>
    <ElPageHeader v-bind="({...filteredProps(), ...$attrs} as any)" @back="emit('back')">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.breadcrumb" #breadcrumb>
            <slot name="breadcrumb" />
        </template>
        <template v-if="$slots.icon" #icon>
            <slot name="icon" />
        </template>
        <template v-if="$slots.title" #title>
            <slot name="title" />
        </template>
        <template v-if="$slots.content" #content>
            <slot name="content" />
        </template>
        <template v-if="$slots.extra" #extra>
            <slot name="extra" />
        </template>
    </ElPageHeader>
</template>

<script setup lang="ts">
    import {ElPageHeader} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        icon?: any
        title?: string
        content?: string
    }>()

    const emit = defineEmits<{
        back: []
    }>()

    defineSlots<{
        default?(): unknown
        breadcrumb?(): unknown
        icon?(): unknown
        title?(): unknown
        content?(): unknown
        extra?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/page-header';
</style>
