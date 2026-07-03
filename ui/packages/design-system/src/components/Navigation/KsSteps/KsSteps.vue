<template>
    <div v-if="variant === 'bar'" class="ks-stepbar" role="list" v-bind="$attrs">
        <slot />
    </div>
    <ElSteps
        v-else
        :class="{'kel-steps--small': size === 'small'}"
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElSteps>
</template>

<script setup lang="ts">
    import {ElSteps} from "element-plus"
    import {provide, toRef} from "vue"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        active?: number
        direction?: "horizontal" | "vertical"
        space?: string | number
        finishStatus?: string
        processStatus?: string
        simple?: boolean
        alignCenter?: boolean
        size?: "default" | "small"
        variant?: "steps" | "bar"
    }>()

    const filteredProps = useFilteredProps(props, ["size", "variant"])

    // In the "bar" variant each child KsStep renders itself as a single progress segment (instead of
    // an ElStep), reading this injected variant — the same provide/inject pattern ElSteps/ElStep use.
    provide("ksStepsVariant", toRef(props, "variant"))

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/steps';

    // variant="bar": a label-less, equal-width segmented progress meter. State comes from each
    // KsStep's `status` (see barSegments): filled = a passed step, active = current, upcoming = rest.
    .ks-stepbar {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2, 0.5rem);
        width: 100%;

        &__seg {
            flex: 1 1 0;
            height: 0.375rem;
            border-radius: var(--ks-radius-xl);
            background: var(--ks-border-default);
            transition: background 0.18s ease;

            &.is-filled { background: var(--ks-primary-500); }
            &.is-active { background: var(--ks-primary-300); }
            &.is-upcoming { background: var(--ks-border-default); }
        }
    }
</style>
