<template>
    <KsTooltip
        v-if="tooltip"
        :content="tooltip"
        v-bind="tooltipPlacement ? {placement: tooltipPlacement} : {}"
    >
        <ElButton
            :aria-label="tooltip"
            v-bind="({...filteredProps(), ...$attrs} as any)"
            @click="emit('click', $event)"
            plain
        >
            <template v-if="$slots.default" #default>
                <slot />
            </template>
            <template v-if="$slots.loading" #loading>
                <slot name="loading" />
            </template>
            <template v-if="$slots.icon" #icon>
                <slot name="icon" />
            </template>
        </ElButton>
    </KsTooltip>
    <ElButton
        v-else
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @click="emit('click', $event)"
        plain
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.loading" #loading>
            <slot name="loading" />
        </template>
        <template v-if="$slots.icon" #icon>
            <slot name="icon" />
        </template>
    </ElButton>
</template>

<script setup lang="ts">
    import type {Component} from "vue"

    import {ElButton} from "element-plus"

    import {useFilteredProps} from "../../../utils/filteredProps"
    import KsTooltip from "../../Feedback/KsTooltip.vue"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        type?: "default" | "primary" | "success" | "warning" | "info" | "danger" | "text" | ""
        size?: "small" | "default" | "large" | ""
        disabled?: boolean
        icon?: string | Component
        nativeType?: "button" | "submit" | "reset"
        loading?: boolean
        text?: boolean
        link?: boolean
        bg?: boolean
        autofocus?: boolean
        round?: boolean
        circle?: boolean
        color?: string
        tag?: string | Component
        tooltip?: string
        tooltipPlacement?: string
    }>()

    const emit = defineEmits<{
        click: [evt: MouseEvent]
    }>()

    defineSlots<{
        default?(): unknown
        loading?(): unknown
        icon?(): unknown
    }>()

    const filteredProps = useFilteredProps(props, ["tooltip", "tooltipPlacement"])
</script>

<style lang="scss">
    @use "sass:map";
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/button';
    @use '../../../assets/styles/color-palette' as palette;

    $tag-color-map: (
        primary: (
            border: var(--ks-btn-primary-bg-default),
            text: var(--ks-btn-primary-text),
        ),
        success: (
            border: var(--ks-border-success),
            text: var(--ks-text-success),
        ),
        warning: (
            border: var(--ks-border-warning),
            text: var(--ks-text-warning),
        ),
        danger: (
            border: var(--ks-border-error),
            text: var(--ks-text-error),
        ),
        error: (
            border: var(--ks-border-error),
            text: var(--ks-text-error),
        ),
        info: (
            border: var(--ks-border-info),
            text: var(--ks-text-info),
        ),
    );

    .kel-button {
        --kel-button-font-weight: 600;
        --kel-button-border-color: var(--ks-btn-secondary-border-default);
        --kel-button-disabled-text-color: var(--ks-text-inactive);

        &.kel-button--small {
            border-radius: var(--kel-border-radius-small);
        }

        &.is-plain:not(.is-text) {
            --kel-button-border-color: var(--ks-btn-secondary-border-default);
            --kel-button-bg-color: var(--ks-btn-secondary-bg-default);

            --kel-button-hover-text-color: var(--ks-text-primary);
            --kel-button-hover-bg-color: var(--ks-btn-secondary-bg-hover);
            --kel-button-hover-border-color: var(--ks-btn-secondary-border-hover);

            --kel-button-active-text-color: var(--ks-text-primary);
            --kel-button-active-bg-color: var(--ks-btn-secondary-bg-active);
            --kel-button-active-border-color: var(--ks-btn-secondary-border-active);

            @each $i, $colors in $tag-color-map {
                &.kel-button--#{$i} {
                    --kel-button-text-color: #{map.get($colors, text)};

                    --kel-button-hover-text-color: #{map.get($colors, text)};
                    --kel-button-hover-border-color: #{map.get($colors, border)};

                    --kel-button-active-text-color: #{map.get($colors, text)};
                    --kel-button-active-bg-color: var(--ks-btn-secondary-bg-active);
                    --kel-button-active-border-color: var(--ks-btn-secondary-border-default);
                }
            }

            &.is-disabled {
                background-color: var(--ks-btn-secondary-bg-inactive);
                border-color: var(--ks-btn-secondary-border-inactive);
                color: var(--ks-text-inactive);
            }


            &.kel-button--primary {
                --kel-button-bg-color: var(--ks-btn-primary-bg-default);
                --kel-button-border-color: var(--ks-btn-primary-bg-default);

                --kel-button-hover-text-color: var(--ks-btn-primary-text);
                --kel-button-hover-bg-color: var(--ks-btn-primary-bg-hover);
                --kel-button-hover-border-color: var(--ks-btn-primary-bg-hover);

                --kel-button-active-text-color: var(--ks-btn-primary-text);
                --kel-button-active-bg-color: var(--ks-btn-primary-bg-active);
                --kel-button-active-border-color: var(--ks-btn-primary-bg-active);
            }
        }

        &.is-text {
            &:hover {
                background-color: var(--ks-bg-hover);
                border: 1px solid var(--ks-btn-secondary-border-hover);
            }

            &:active {
                background-color: var(--ks-btn-secondary-bg-active);
                border: 0;
            }
        }

        &.kel-button--primary:not(.is-text):not(.is-link).is-disabled {
            background-color: var(--ks-btn-primary-bg-inactive);
            border-color: var(--ks-btn-primary-bg-inactive);
            color: var(--ks-text-inactive);
        }
    }
</style>
