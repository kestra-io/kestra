<template>
    <ElDescriptionsItem v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.label" #label>
            <slot name="label" />
        </template>
    </ElDescriptionsItem>
</template>

<script setup lang="ts">
    import {ElDescriptionsItem} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        label?: string
        span?: number
        rowspan?: number
        width?: string | number
        minWidth?: string | number
        labelWidth?: string | number
        align?: "left" | "center" | "right"
        labelAlign?: "left" | "center" | "right"
        className?: string
        labelClassName?: string
    }>()

    defineSlots<{
        default?(): unknown
        label?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>
