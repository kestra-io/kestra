<template>
    <ElStep
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.icon" #icon>
            <slot name="icon" />
        </template>
        <template v-if="$slots.title" #title>
            <slot name="title" />
        </template>
        <template v-if="$slots.description" #description>
            <slot name="description" />
        </template>
    </ElStep>
</template>

<script setup lang="ts">
    import {ElStep} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        title?: string
        description?: string
        icon?: any
        status?: string
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
        icon?(): unknown
        title?(): unknown
        description?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/step';

    .kel-steps {
        .is-process {
            color: var(--ks-white);
        }

        .kel-step__head {
            &.is-process .kel-step__icon {
                border-color: var(--ks-white);
                box-shadow: 0 1px 3px 0 #7614B880,
                0 5px 5px 0 #7614B86E,
                0 11px 7px 0 #7614B842,
                0 20px 8px 0 #7614B814,
                0 31px 9px 0 #7614B803;
            }
            .kel-step__icon {
                border: 1px solid var(--ks-border-default);
                border-radius: 50%;
                background-color: var(--ks-bg-input);
            }

            &.is-success .kel-step__icon {
                box-shadow: 0 2px 3px 0 #29DB9726,
                0 6px 6px 0 #29DB9721,
                0 14px 8px 0 #29DB9714,
                0 25px 10px 0 #29DB9705,
                0 39px 11px 0 #29DB9700;

            }

            .kel-step__line {
                width: 1px;
            }
        }

        // Horizontal orientation: the connector spans the gap between steps,
        // so it needs a height (not a width — width: 1px collapses it).
        .kel-step.is-horizontal .kel-step__head .kel-step__line {
            width: auto;
            height: 2px;
        }
    }
</style>
