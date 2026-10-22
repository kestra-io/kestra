<template>
    <ElText v-bind="({...filteredProps(), ...$attrs} as TextProps)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElText>
</template>

<script setup lang="ts">
    import {ElText, type TextProps} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        type?: "" | "primary" | "success" | "warning" | "danger" | "info"
        size?: "small" | "default" | "large"
        truncated?: boolean
        lineClamp?: string | number
        tag?: string
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/text';
</style>

<style>
    .kel-text.kel-text--primary {
        --kel-text-color: var(--ks-text-link);
    }

    .kel-text.kel-text--success {
        --kel-text-color: var(--ks-text-success);
    }

    .kel-text.kel-text--warning {
        --kel-text-color: var(--ks-text-warning);
    }

    .kel-text.kel-text--danger {
        --kel-text-color: var(--ks-text-error);
    }

    .kel-text.kel-text--info {
        --kel-text-color: var(--ks-text-info);
    }
</style>
