<template>
    <ElLink
        v-bind="({...filteredProps(), ...$attrs} as any)"
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
    import {ElLink} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        type?: "" | "default" | "primary" | "success" | "warning" | "danger" | "info"
        underline?: "" | "always" | "hover" | "never"
        disabled?: boolean
        href?: string
        target?: string
        icon?: any
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
</style>
