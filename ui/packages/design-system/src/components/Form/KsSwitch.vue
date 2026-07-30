<template>
    <ElSwitch
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    />
</template>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use '../../assets/styles/color-palette' as palette;
    @use 'element-plus/theme-chalk/src/switch';

    .kel-switch {
        height: 20px;

        .kel-switch__core {
            width: 42px;
            min-width: 42px;
            height: 20px;
            border: none;
            border-radius: 16px;
            background-color: var(--ks-toggle-default);
            transition: background-color 0.2s ease;

            .kel-switch__action {
                width: 14px;
                height: 14px;
                top: 3px;
                left: 3px;
                border-radius: 7px;
                background-color: palette.$base-gray-neutral-white;
                box-shadow: 0 1px 4px var(--ks-shadow-element);
                transition: left 0.28s cubic-bezier(0.34, 1.56, 0.64, 1), width 0.18s ease, background-color 0.2s ease;
            }
        }

        &:hover:not(.is-disabled):not(.is-checked) .kel-switch__core {
            background-color: var(--ks-toggle-hover);
        }

        &.is-checked .kel-switch__core {
            background-color: var(--ks-toggle-active);

            .kel-switch__action {
                left: calc(100% - 17px);
            }
        }

        &:active:not(.is-disabled) .kel-switch__core .kel-switch__action {
            width: 18px;
        }

        &.is-checked:active:not(.is-disabled) .kel-switch__core .kel-switch__action {
            width: 18px;
            left: calc(100% - 21px);
        }

        @media (prefers-reduced-motion: reduce) {
            .kel-switch__core,
            .kel-switch__core .kel-switch__action {
                transition: none;
            }

            &:active .kel-switch__core .kel-switch__action {
                width: 14px;
            }
        }


        &.is-disabled {
            opacity: 1;

            .kel-switch__core {
                background-color: var(--ks-toggle-inactive);

                .kel-switch__action {
                    background-color: var(--ks-toggle-default);
                }
            }
        }

        &.is-disabled.is-checked .kel-switch__core {
            background-color: var(--ks-toggle-active);
        }

        .kel-switch__label {
            color: var(--ks-text-primary);
        }
    }
</style>

<script setup lang="ts">
    import {ElSwitch} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<boolean | string | number>()

    const props = withDefaults(defineProps<{
        disabled?: boolean
        activeText?: string
        inactiveText?: string
        activeActionIcon?: any
        inactiveActionIcon?: any
        size?: "large" | "default" | "small"
        activeValue?: boolean | string | number
        inactiveValue?: boolean | string | number
    }>(), {
        activeText: undefined,
        inactiveText: undefined,
        activeActionIcon: undefined,
        inactiveActionIcon: undefined,
        size: undefined,
        activeValue: undefined,
        inactiveValue: undefined,
    })

    const emit = defineEmits<{
        change: [value: boolean | string | number]
    }>()

    const filteredProps = useFilteredProps(props)
</script>
