<template>
    <ElProgress :strokeLinecap="strokeLinecap" v-bind="({...filteredProps(), ...$attrs} as ProgressProps)">
        <slot/>
    </ElProgress>
</template>

<script setup lang="ts">
    import {ElProgress, type ProgressProps} from "element-plus"
    import {usePassedProps} from "../../utils/filteredProps"
    import {computed, type HTMLAttributes} from "vue"
    import type {NativeAttributes} from "../../utils/nativeAttributes"

    export interface KsProgressProps extends ProgressProps {
        left?: number
        radius?: number | string
    }

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<KsProgressProps & /* @vue-ignore */ Omit<NativeAttributes<HTMLAttributes>, keyof KsProgressProps>>(), {
        strokeLinecap: "square",
    })

    const left = computed(() => `${props.left ?? 0}%`)
    const borderRadius = computed(() =>
        props.radius === undefined
            ? "var(--kel-border-radius-small)"
            : typeof props.radius === "number" ? `${props.radius}px` : props.radius,
    )

    const filteredProps = usePassedProps(props, ["left", "radius"])
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/progress';
</style>

<style>
</style>

<style scoped lang="scss">
    @use '../../assets/styles/el-ns';
</style>

<style scoped>
    .kel-progress {
        & :deep(.kel-progress-bar__outer) {
            border-radius: v-bind(borderRadius);
            background-color: var(--ks-bg-hover);
        }

        & :deep(.kel-progress-bar__inner) {
            border-radius: v-bind(borderRadius);
            left: v-bind(left);
        }
    }
</style>
