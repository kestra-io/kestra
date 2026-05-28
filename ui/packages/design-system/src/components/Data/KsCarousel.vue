<template>
    <ElCarousel
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="(current, prev) => emit('change', current, prev)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElCarousel>
</template>

<script setup lang="ts">
    import {ElCarousel} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        initialIndex?: number
        height?: string
        trigger?: "hover" | "click"
        autoplay?: boolean
        interval?: number
        indicatorPosition?: "" | "none" | "outside"
        arrow?: "always" | "hover" | "never"
        type?: "" | "card"
        cardScale?: number
        loop?: boolean
        direction?: "horizontal" | "vertical"
        pauseOnHover?: boolean
        motionBlur?: boolean
    }>()

    const emit = defineEmits<{
        change: [current: number, prev: number]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/carousel';
    @use 'element-plus/theme-chalk/src/carousel-item';
</style>
