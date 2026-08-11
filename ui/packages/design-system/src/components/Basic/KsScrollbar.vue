<template>
    <ElScrollbar ref="scrollbarRef" v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElScrollbar>
</template>

<script setup lang="ts">
    import {useTemplateRef} from "vue"
    import {ElScrollbar} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        maxHeight?: string | number
        height?: string | number
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const scrollbarRef = useTemplateRef<InstanceType<typeof ElScrollbar>>("scrollbarRef")

    const filteredProps = useFilteredProps(props)

    defineExpose({
        scrollTo: (...args: any[]) => (scrollbarRef.value?.scrollTo as any)?.(...args),
        setScrollTop: (top: number) => scrollbarRef.value?.setScrollTop(top),
        setScrollLeft: (left: number) => scrollbarRef.value?.setScrollLeft(left),
        update: () => scrollbarRef.value?.update(),
    })
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/scrollbar';
</style>
