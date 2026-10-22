<template>
    <ElBadge
        v-bind="({...filteredProps(), ...$attrs} as BadgeProps)"
        :class="{'kel-badge--inline': inline, 'kel-badge--small': size === 'small'}"
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
        type?: "primary" | "success" | "warning" | "danger" | "info"
        max?: number
        isDot?: boolean
        hidden?: boolean
        showZero?: boolean
        inline?: boolean
        size?: "small" | "default"
    }>()

    const filteredProps = useFilteredProps(props, ["inline", "size"])

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/badge';
    @use "../../assets/styles/color-palette" as palette;

    .kel-badge .kel-badge__content--danger {
        background-color: palette.$base-red-500;
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
