<template>
    <ElCarouselItem v-bind="({...filteredProps(), ...$attrs} as CarouselItemProps)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElCarouselItem>
</template>

<script setup lang="ts">
    import {ElCarouselItem, type CarouselItemProps} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        name?: string
        label?: string | number
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>
