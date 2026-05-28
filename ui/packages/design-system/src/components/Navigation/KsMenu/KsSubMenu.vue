<template>
    <ElSubMenu v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.title" #title>
            <slot name="title" />
        </template>
    </ElSubMenu>
</template>

<script setup lang="ts">
    import {ElSubMenu} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        index: string
        showTimeout?: number
        hideTimeout?: number
        popperClass?: string
        disabled?: boolean
        teleported?: boolean
        popperOffset?: number
    }>()

    defineSlots<{
        default?(): unknown
        title?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>
