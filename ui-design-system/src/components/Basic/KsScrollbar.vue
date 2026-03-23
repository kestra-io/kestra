<script setup lang="ts">
    import {ref} from "vue"
    import {ElScrollbar, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        maxHeight?: string | number
        height?: string | number
        always?: boolean
        wrapClass?: string | string[]
        wrapStyle?: string | object
        viewClass?: string | string[]
        viewStyle?: string | object
        noresize?: boolean
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()

    const scrollbarRef = ref<InstanceType<typeof ElScrollbar>>()

    defineExpose({
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        scrollTo: (...args: any[]) => (scrollbarRef.value?.scrollTo as any)(...args),
        setScrollTop: (top: number) => scrollbarRef.value?.setScrollTop(top),
        setScrollLeft: (left: number) => scrollbarRef.value?.setScrollLeft(left),
        update: () => scrollbarRef.value?.update(),
        wrapRef: scrollbarRef,
    })
</script>

<template>
    <el-scrollbar ref="scrollbarRef" v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default><slot /></template>
    </el-scrollbar>
</template>
