<template>
    <ElScrollbar ref="scrollbarRef" v-bind="({...filteredProps(), ...$attrs} as ScrollbarProps)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElScrollbar>
</template>

<script setup lang="ts">
    import {useTemplateRef} from "vue"
    import {ElScrollbar, type ScrollbarInstance, type ScrollbarProps} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        maxHeight?: string | number
        height?: string | number
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const scrollbarRef = useTemplateRef<ScrollbarInstance>("scrollbarRef")

    const filteredProps = useFilteredProps(props)

    function scrollTo(xCord: number, yCord?: number): void
    function scrollTo(options: ScrollToOptions): void
    function scrollTo(arg: number | ScrollToOptions, yCord?: number): void {
        if (typeof arg === "number") {
            scrollbarRef.value?.scrollTo(arg, yCord)
        } else {
            scrollbarRef.value?.scrollTo(arg)
        }
    }

    defineExpose({
        scrollTo,
        setScrollTop: (top: number) => scrollbarRef.value?.setScrollTop(top),
        setScrollLeft: (left: number) => scrollbarRef.value?.setScrollLeft(left),
        update: () => scrollbarRef.value?.update(),
    })
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/scrollbar';
</style>
