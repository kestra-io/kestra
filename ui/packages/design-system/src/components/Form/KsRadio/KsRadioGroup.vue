<template>
    <ElRadioGroup
        v-model="model"
        :class="props.size ? `kel-radio-group--${props.size}` : undefined"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElRadioGroup>
</template>

<script setup lang="ts">
    import {ElRadioGroup} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<string | number | boolean>()

    const props = defineProps<{
        disabled?: boolean
        size?: "large" | "default" | "small"
    }>()

    const emit = defineEmits<{
        change: [value: any]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/radio-group';

    .kel-radio-group {
        .kel-radio__label {
            font-size: var(--kel-font-size-small);
        }

        &:not(.kel-radio-group--small) {
            .kel-radio-button__inner {
                font-size: var(--kel-font-size-small);
            }
        }

        &.kel-radio-group--large {
            .kel-radio__label {
                font-size: var(--kel-font-size-base);
            }
        }
    }

    // @FIXME: should not be part of design system, should be inside filter implementation
    .kel-radio-group.filter {
        padding: 1px 4px;
        box-shadow: 0 0 0 1px var(--ks-border-default) inset;
        background-color: var(--ks-bg-input);
        border-radius: var(--kel-border-radius-base);
        height: var(--kel-component-size);

        .kel-radio-button {
            display: inline-flex;
        }

        .kel-radio-button__inner {
            background-color: var(--ks-bg-input);
            padding: 4px 15px;
            border: 0 !important;
            box-shadow: none;
            border-radius: var(--kel-border-radius-base) !important;
        }

        .kel-radio-button__original-radio:checked + .kel-radio-button__inner {
            box-shadow: none;
            background: var(--ks-border-default);
        }
    }
</style>
