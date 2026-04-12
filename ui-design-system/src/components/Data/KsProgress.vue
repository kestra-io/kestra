<script setup lang="ts">
    import {ElProgress, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"
    import {computed, ref} from "vue";

    provideGlobalConfig({namespace: "kel"})

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
        showText: undefined,
    })

    const filteredProps = useFilteredProps(props)

    const left = computed(() => {
        return `${props.left ?? 0}%`;
    });
</script>

<template>
    <el-progress v-bind="({...filteredProps(), ...$attrs} as any)" />
</template>

<style lang="scss" scoped>
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/progress';

    .kel-progress {
        :deep(.kel-progress-bar__outer) {
            border-radius: var(--kel-border-radius-small);

            background-color: var(--ks-gray-100);

            html.dark & {
                background-color: var(--ks-gray-900);
            }
        }

        :deep(.kel-progress-bar__inner) {
            border-radius: var(--kel-border-radius-small);
            left: v-bind(left);
        }
    }

</style>