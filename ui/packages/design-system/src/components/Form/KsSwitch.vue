<template>
    <ElSwitch
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    />
</template>

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

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use '../../assets/styles/color-palette' as palette;
    @use 'element-plus/theme-chalk/src/switch';

    /**
     * 2.0 toggle (Figma "Toggle" component): 42x20 pill, 14px white knob with a
     * subtle drop-shadow. Track colours come from the shared `--ks-toogle-*`
     * tokens (declared in ks-theme-{light,dark,dark-2}.scss), so light / dark /
     * dark-2 theme automatically — no component-local overrides needed.
     * (Token name keeps the upstream Figma spelling "toogle".)
     */
    .kel-switch {
        height: 20px;

        .kel-switch__core {
            width: 42px;
            min-width: 42px;
            height: 20px;
            border: none;
            border-radius: 16px;
            background-color: var(--ks-toogle-bg-default);

            .kel-switch__action {
                width: 14px;
                height: 14px;
                top: 3px;
                left: 3px;
                border-radius: 7px;
                background-color: palette.$base-white;
                box-shadow: 0 1px 4px var(--ks-shadow-element);
                transition: left 0.2s ease, width 0.2s ease;
            }
        }

        &:hover:not(.is-disabled):not(.is-checked) .kel-switch__core {
            background-color: var(--ks-toogle-bg-hover);
        }

        &.is-checked .kel-switch__core {
            background-color: var(--ks-toogle-bg-active);

            .kel-switch__action {
                left: calc(100% - 17px);
            }
        }

        /* Hover: the knob widens (stretching toward the direction it would slide). */
        &:hover:not(.is-disabled) .kel-switch__core .kel-switch__action {
            width: 18px;
        }

        &.is-checked:hover:not(.is-disabled) .kel-switch__core .kel-switch__action {
            left: calc(100% - 21px);
        }

        &.is-disabled {
            opacity: 1;

            .kel-switch__core {
                background-color: var(--ks-toogle-bg-inactive);

                .kel-switch__action {
                    background-color: var(--ks-toogle-bg-default);
                }
            }
        }

        .kel-switch__label {
            color: var(--ks-text-primary);
        }
    }
</style>
