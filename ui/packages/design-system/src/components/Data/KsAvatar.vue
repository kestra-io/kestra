<template>
    <ElAvatar v-bind="({...filteredProps(), ...$attrs} as AvatarProps)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElAvatar>
</template>

<script setup lang="ts">
    import {ElAvatar, type AvatarProps} from "element-plus"
    import type {Component, HTMLAttributes} from "vue"

    import {useFilteredProps} from "../../utils/filteredProps"
    import type {NativeAttributes} from "../../utils/nativeAttributes"

    defineOptions({inheritAttrs: false})

    interface KsAvatarProps {
        size?: number | "large" | "default" | "small"
        shape?: "circle" | "square"
        src?: string
        alt?: string
        fit?: "fill" | "contain" | "cover" | "none" | "scale-down"
        icon?: string | Component
    }

    const props = defineProps<KsAvatarProps & /* @vue-ignore */ Omit<NativeAttributes<HTMLAttributes>, keyof KsAvatarProps>>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/avatar';
</style>

<style>
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
