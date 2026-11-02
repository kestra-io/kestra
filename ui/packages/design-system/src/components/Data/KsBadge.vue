<template>
    <ElBadge
        v-bind="({...filteredProps(), ...$attrs} as BadgeProps)"
        :type="type === 'neutral' ? undefined : type"
        :class="{'kel-badge--inline': inline, 'kel-badge--small': size === 'small', 'ks-badge--neutral': type === 'neutral'}"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElBadge>
</template>

<script setup lang="ts">
    import {ElBadge, type BadgeProps} from "element-plus"

    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        value?: string | number
        /** `neutral` is a grey pill for counts that sit next to content without competing with it. */
        type?: "primary" | "success" | "warning" | "danger" | "info" | "neutral"
        max?: number
        isDot?: boolean
        hidden?: boolean
        showZero?: boolean
        inline?: boolean
        size?: "small" | "default"
    }>()

    const filteredProps = useFilteredProps(props, ["inline", "size", "type"])

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/badge';
</style>

<style>
    .kel-badge .kel-badge__content--danger {
        background-color: var(--ks-red-500);
    }

    .kel-badge.ks-badge--neutral .kel-badge__content {
        background-color: var(--ks-bg-tag);
        color: var(--ks-text-secondary);
    }

    .kel-badge--inline {
        display: inline-flex;
        align-items: center;
    }

    .kel-badge--inline .kel-badge__content {
        position: static;
        transform: none;
        border: none;
        margin: 0;
    }

    .kel-badge--small {
        --kel-badge-size: calc(var(--ks-font-size-2xs) + var(--ks-spacing-1));
        --kel-badge-font-size: var(--ks-font-size-2xs);
        --kel-badge-padding: var(--ks-spacing-1);
    }
</style>
