<template>
    <ElAvatar v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElAvatar>
</template>

<script setup lang="ts">
    import {ElAvatar} from "element-plus"

    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        size?: number | "large" | "default" | "small"
        shape?: "circle" | "square"
        src?: string
        alt?: string
        fit?: "fill" | "contain" | "cover" | "none" | "scale-down"
        icon?: any
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/avatar';

    .kel-avatar {
        --kel-avatar-bg-color: var(--ks-border-default);
        --kel-avatar-text-color: var(--ks-text-primary);

        &.kel-avatar--small {
            font-size: 65%;
        }

        html.dark & {
            --kel-avatar-text-color: var(--ks-white);
        }
    }
</style>
