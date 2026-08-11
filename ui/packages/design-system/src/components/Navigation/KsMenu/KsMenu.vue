<template>
    <ElMenu
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @select="(index, indexPath) => emit('select', index, indexPath)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElMenu>
</template>

<script setup lang="ts">
    import {ElMenu} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        mode?: "horizontal" | "vertical"
        defaultActive?: string
        collapse?: boolean
    }>()

    const emit = defineEmits<{
        select: [index: string, indexPath: string[]]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/menu';
    @use 'element-plus/theme-chalk/src/menu-item-group';
    @use 'element-plus/theme-chalk/src/sub-menu';
</style>
