<template>
    <ElProgress v-bind="({...filteredProps(), ...$attrs} as any)">
        <slot/>
    </ElProgress>
</template>

<script setup lang="ts">
    import {ElProgress} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"
    import {computed} from "vue"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        left?: number
        percentage?: number
        type?: "line" | "circle" | "dashboard"
        strokeWidth?: number
        color?: string | object | ((pct: number) => string)
        showText?: boolean
        status?: "" | "success" | "exception" | "warning"
        striped?: boolean
        stripedFlow?: boolean
        radius?: number | string
    }>(), {
        left: undefined,
        percentage: undefined,
        type: undefined,
        strokeWidth: undefined,
        color: undefined,
        showText: undefined,
        status: undefined,
        strokeLinecap: "square",
        radius: undefined,
    })

    const left = computed(() => `${props.left ?? 0}%`)
    const borderRadius = computed(() =>
        props.radius === undefined
            ? "var(--kel-border-radius-small)"
            : typeof props.radius === "number" ? `${props.radius}px` : props.radius,
    )

    const filteredProps = useFilteredProps(props, ["radius"])
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/progress';
</style>

<style lang="scss" scoped>
    @use '../../assets/styles/el-ns';

    .kel-progress {
        :deep(.kel-progress-bar__outer) {
            border-radius: v-bind(borderRadius);

            background-color: var(--ks-bg-hover);
        }

        :deep(.kel-progress-bar__inner) {
            border-radius: v-bind(borderRadius);
            left: v-bind(left);
        }
    }

</style>
