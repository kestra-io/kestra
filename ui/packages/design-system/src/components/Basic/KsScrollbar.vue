<template>
    <ElScrollbar ref="scrollbarRef" v-bind="({...filteredProps(), ...$attrs} as ScrollbarProps)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElScrollbar>
</template>

<script setup lang="ts">
    import type {NativeAttributes} from "../../utils/nativeAttributes"
    import {useTemplateRef, type HTMLAttributes} from "vue"
    import {ElScrollbar, type ScrollbarInstance, type ScrollbarProps} from "element-plus"
    import {usePassedProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<ScrollbarProps & /* @vue-ignore */ Omit<NativeAttributes<HTMLAttributes>, keyof ScrollbarProps>>()

    defineSlots<{
        default?(): unknown
    }>()

    const scrollbarRef = useTemplateRef<ScrollbarInstance>("scrollbarRef")

    const filteredProps = usePassedProps(props)

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

<style>
</style>
