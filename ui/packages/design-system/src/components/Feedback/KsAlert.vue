<template>
    <ElAlert v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.title" #title>
            <slot name="title" />
        </template>
    </ElAlert>
</template>

<script setup lang="ts">
    import {ElAlert} from "element-plus"

    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        type?: "success" | "warning" | "info" | "error"
        title?: string
        description?: string
        closable?: boolean
        showIcon?: boolean
        center?: boolean
        effect?: "light" | "dark"
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
        title?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns' as *;
    @use 'element-plus/theme-chalk/src/alert' as *;
    @use "element-plus/theme-chalk/src/common/var" as *;

    .kel-alert {
        --kel-alert-description-font-size: var(--ks-font-size-sm);

        @each $type in $types {
            &.kel-alert--#{$type}.is-light {
                border: 1px solid var(--ks-border-#{$type});
                background-color: var(--ks-background-#{$type});
                #{--kel-color-#{$type}}: var(--ks-content-#{$type});
            }
        }
    }
</style>
