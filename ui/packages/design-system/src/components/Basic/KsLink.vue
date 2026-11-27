<template>
    <ElLink
        v-bind="({...filteredProps(), ...$attrs} as LinkProps)"
        @click="emit('click', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.icon" #icon>
            <slot name="icon" />
        </template>
    </ElLink>
</template>

<script setup lang="ts">
    import type {AnchorHTMLAttributes} from "vue"
    import {ElLink, type LinkProps} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"
    import type {NativeAttributes} from "../../utils/nativeAttributes"

    defineOptions({inheritAttrs: false})

    interface KsLinkProps {
        type?: "" | "default" | "primary" | "success" | "warning" | "danger" | "info"
        underline?: "" | "always" | "hover" | "never"
        disabled?: boolean
        href?: string
        target?: string
        icon?: LinkProps["icon"]
    }

    const props = defineProps<KsLinkProps & /* @vue-ignore */ Omit<NativeAttributes<AnchorHTMLAttributes>, keyof KsLinkProps>>()

    const emit = defineEmits<{
        click: [evt: MouseEvent]
    }>()

    defineSlots<{
        default?(): unknown
        icon?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/link';
</style>

<style>
    .kel-link {
        font-size: var(--ks-font-size-base);
    }
</style>
