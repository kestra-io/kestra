<template>
    <ElTooltip
        :persistent="false"
        :hideAfter="0"
        transition=""
        :effect="props.effect ?? 'light'"
        v-bind="({...filteredProps(), ...$attrs} as UseTooltipProps)"
        :popperClass="['ks-tooltip', props.popperClass]"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.content" #content>
            <slot name="content" />
        </template>
    </ElTooltip>
</template>

<script lang="ts">
    import type {UseTooltipProps} from "element-plus"

    export interface KsTooltipProps extends Omit<UseTooltipProps, "trigger"> {
        effect?: "light" | "dark"
        /** "manual" attaches no trigger, so only `visible` opens the tooltip. */
        trigger?: UseTooltipProps["trigger"] | "manual"
    }
</script>

<script setup lang="ts">
    import {ElTooltip} from "element-plus"
    import {usePassedProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<KsTooltipProps>()

    defineSlots<{
        default?(): unknown
        content?(): unknown
    }>()

    const filteredProps = usePassedProps(props, ["effect", "popperClass"])
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/tooltip';
</style>

<style>
    .kel-popper.ks-tooltip {
        &.is-light,
        &.is-dark {
            background: var(--ks-bg-input);
            color: var(--ks-text-primary);
            border: 1px solid var(--ks-border-default);
            box-shadow: 0 2px 6px var(--ks-shadow-element);
        }

        &.is-light .kel-popper__arrow::before,
        &.is-dark .kel-popper__arrow::before {
            background: var(--ks-bg-input);
            border: 1px solid var(--ks-border-default);
        }
    }
</style>
