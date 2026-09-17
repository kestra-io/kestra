<template>
    <ElTooltip
        :persistent="false"
        :hideAfter="0"
        transition=""
        :effect="props.effect ?? 'light'"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        :popperClass="popperClass"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.content" #content>
            <slot name="content" />
        </template>
    </ElTooltip>
</template>

<script setup lang="ts">
    import {computed, useAttrs} from "vue"
    import {ElTooltip} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        content?: string
        trigger?: "click" | "hover" | "focus" | "contextmenu" | "manual"
        placement?: string
        effect?: "light" | "dark"
        enterable?: boolean
        rawContent?: boolean
        disabled?: boolean
        autoClose?: boolean | number
    }>(), {
        content: undefined,
        trigger: undefined,
        placement: undefined,
        effect: undefined,
        enterable: undefined,
        autoClose: undefined,
    })

    defineSlots<{
        default?(): unknown
        content?(): unknown
    }>()

    const attrs = useAttrs()

    const popperClass = computed(() => {
        const extra = (attrs.popperClass as string | undefined) ?? ""
        return `ks-tooltip ${extra}`.trim()
    })

    const filteredProps = useFilteredProps(props, ["effect"])
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/tooltip';

    .el-popper.ks-tooltip {
        &.is-light,
        &.is-dark {
            background: var(--ks-bg-input);
            color: var(--ks-text-primary);
            border: 1px solid var(--ks-border-default);
            box-shadow: 0 2px 6px var(--ks-shadow-element);
        }

        &.is-light .el-popper__arrow::before,
        &.is-dark .el-popper__arrow::before {
            background: var(--ks-bg-input);
            border: 1px solid var(--ks-border-default);
        }
    }
</style>
