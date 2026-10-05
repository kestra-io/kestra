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
    import {ElLink, type LinkProps} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        type?: "" | "default" | "primary" | "success" | "warning" | "danger" | "info"
        underline?: "" | "always" | "hover" | "never"
        disabled?: boolean
        href?: string
        target?: string
        icon?: LinkProps["icon"]
    }>()

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

    .kel-link {
        font-size: var(--ks-font-size-base);
    }
</style>
