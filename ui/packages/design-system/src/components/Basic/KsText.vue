<template>
    <ElText v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElText>
</template>

<script setup lang="ts">
    import {ElText} from "element-plus"
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

    $variants: (
        "primary": "link",
        "success": "success",
        "warning": "warning",
        "danger": "error",
        "info": "info",
    );

    @each $variant, $token in $variants {
        .kel-text.kel-text--#{$variant} {
            --kel-text-color: var(--ks-text-#{$token});
        }
    }
</style>
