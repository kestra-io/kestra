<template>
    <ElAvatar v-bind="({...filteredProps(), ...$attrs} as AvatarProps)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElAvatar>
</template>

<script setup lang="ts">
    import {ElAvatar, type AvatarProps} from "element-plus"
    import type {Component} from "vue"

    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        size?: number | "large" | "default" | "small"
        shape?: "circle" | "square"
        src?: string
        alt?: string
        fit?: "fill" | "contain" | "cover" | "none" | "scale-down"
        icon?: string | Component
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
            font-size: var(--ks-font-size-2xs);
        }

        html.dark & {
            --kel-avatar-text-color: var(--ks-white);
        }
    }
</style>
