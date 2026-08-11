<template>
    <ElMenuItem
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @click="emit('click', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.title" #title>
            <slot name="title" />
        </template>
    </ElMenuItem>
</template>

<script setup lang="ts">
    import {ElMenuItem} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        index?: string
        disabled?: boolean
        route?: string | object
    }>()

    const emit = defineEmits<{
        click: [item: any]
    }>()

    defineSlots<{
        default?(): unknown
        title?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/menu-item';
</style>
