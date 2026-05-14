<template>
    <ElProgress v-bind="({...filteredProps(), ...$attrs} as any)" />
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
    }>(), {
        left: undefined,
        percentage: undefined,
        type: undefined,
        strokeWidth: undefined,
        color: undefined,
        showText: undefined,
        status: undefined,
    })

    const left = computed(() => `${props.left ?? 0}%`)

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/progress';
</style>

<style lang="scss" scoped>
    @use '../../assets/styles/el-ns';

    .kel-progress {
        :deep(.kel-progress-bar__outer) {
            border-radius: var(--kel-border-radius-small);

            background-color: var(--ks-scrollbar-background);
        }

        :deep(.kel-progress-bar__inner) {
            border-radius: var(--kel-border-radius-small);
            left: v-bind(left);
        }
    }

</style>
