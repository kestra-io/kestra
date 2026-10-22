<template>
    <KsTooltip
        v-if="tooltip"
        :content="tooltip"
        v-bind="tooltipPlacement ? {placement: tooltipPlacement} : {}"
    >
        <ElButton
            :aria-label="tooltip"
            v-bind="({...filteredProps(), ...$attrs})"
            :class="{'is-square': square, 'is-block': block}"
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
        v-bind="({...filteredProps(), ...$attrs})"
        :class="{'is-square': square, 'is-block': block}"
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

<script lang="ts">
    export type KsButtonType = "default" | "primary" | "success" | "warning" | "info" | "danger" | "text" | ""
</script>

<script setup lang="ts">
    import type {Component} from "vue"

    import {ElButton} from "element-plus"

    import {useFilteredProps} from "../../../utils/filteredProps"
    import KsTooltip from "../../Feedback/KsTooltip.vue"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        type?: KsButtonType
        size?: "small" | "default" | "large" | ""
        disabled?: boolean
        icon?: string | object
        nativeType?: "button" | "submit" | "reset"
        loading?: boolean
        text?: boolean
        link?: boolean
        bg?: boolean
        autofocus?: boolean
        round?: boolean
        circle?: boolean
        square?: boolean
        block?: boolean
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

    const filteredProps = useFilteredProps(props, ["tooltip", "tooltipPlacement", "square", "block"])
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/button';
</style>

<style>
    .kel-button {
        --kel-button-font-weight: 600;
        --kel-button-border-color: var(--ks-btn-secondary-border-default);
        --kel-button-disabled-text-color: var(--ks-text-inactive);

        & [class*="kel-icon"] + span {
            margin-left: var(--ks-spacing-2);
        }

        & > span {
            width: var(--ks-button-content-width, auto);
        }

        &.kel-button--small {
            border-radius: var(--kel-border-radius-small);
        }

        &.is-block {
            display: flex;
            width: 100%;
        }

        &.is-square {
            aspect-ratio: 1;
            padding-left: 0;
            padding-right: 0;
        }

        /* Icon-only buttons centre the glyph. vue-material-design-icons nudges its SVG down by
           0.125em (baseline alignment meant for inline-with-text use), which reads as off-centre
           in a round/square button — neutralise it so the icon sits dead centre. */

        &.is-circle,
        &.is-square {
            & .material-design-icon > .material-design-icon__svg {
                bottom: 0;
                left: 50%;
                transform: translateX(-50%);
            }
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

            &.kel-button--primary {
                --kel-button-text-color: var(--ks-btn-primary-text);
                --kel-button-hover-text-color: var(--ks-btn-primary-text);
                --kel-button-hover-border-color: var(--ks-btn-primary-bg-default);
                --kel-button-active-text-color: var(--ks-btn-primary-text);
                --kel-button-active-bg-color: var(--ks-btn-secondary-bg-active);
                --kel-button-active-border-color: var(--ks-btn-secondary-border-default);
            }

            &.kel-button--success {
                --kel-button-text-color: var(--ks-text-success);
                --kel-button-hover-text-color: var(--ks-text-success);
                --kel-button-hover-border-color: var(--ks-border-success);
                --kel-button-active-text-color: var(--ks-text-success);
                --kel-button-active-bg-color: var(--ks-btn-secondary-bg-active);
                --kel-button-active-border-color: var(--ks-btn-secondary-border-default);
            }

            &.kel-button--warning {
                --kel-button-text-color: var(--ks-text-warning);
                --kel-button-hover-text-color: var(--ks-text-warning);
                --kel-button-hover-border-color: var(--ks-border-warning);
                --kel-button-active-text-color: var(--ks-text-warning);
                --kel-button-active-bg-color: var(--ks-btn-secondary-bg-active);
                --kel-button-active-border-color: var(--ks-btn-secondary-border-default);
            }

            &.kel-button--danger {
                --kel-button-text-color: var(--ks-text-error);
                --kel-button-hover-text-color: var(--ks-text-error);
                --kel-button-hover-border-color: var(--ks-border-error);
                --kel-button-active-text-color: var(--ks-text-error);
                --kel-button-active-bg-color: var(--ks-btn-secondary-bg-active);
                --kel-button-active-border-color: var(--ks-btn-secondary-border-default);
            }

            &.kel-button--error {
                --kel-button-text-color: var(--ks-text-error);
                --kel-button-hover-text-color: var(--ks-text-error);
                --kel-button-hover-border-color: var(--ks-border-error);
                --kel-button-active-text-color: var(--ks-text-error);
                --kel-button-active-bg-color: var(--ks-btn-secondary-bg-active);
                --kel-button-active-border-color: var(--ks-btn-secondary-border-default);
            }

            &.kel-button--info {
                --kel-button-text-color: var(--ks-text-info);
                --kel-button-hover-text-color: var(--ks-text-info);
                --kel-button-hover-border-color: var(--ks-border-info);
                --kel-button-active-text-color: var(--ks-text-info);
                --kel-button-active-bg-color: var(--ks-btn-secondary-bg-active);
                --kel-button-active-border-color: var(--ks-btn-secondary-border-default);
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

        &.is-link {
            --kel-button-text-color: var(--ks-text-secondary);
            --kel-button-hover-link-text-color: var(--ks-text-primary);
            --kel-button-active-color: var(--ks-text-primary);

            &.kel-button--primary.is-plain:not(.is-text) {
                --kel-button-text-color: var(--ks-text-link);
                --kel-button-hover-link-text-color: var(--ks-text-link);
                --kel-button-active-color: var(--ks-text-link);
            }
        }

        &.is-text {
            &:hover {
                background-color: var(--ks-bg-hover);
                /* Draw the hover ring with an inset shadow rather than a border: a border adds
                   1px on each side and shifts surrounding layout, a shadow doesn't affect the box. */
                box-shadow: inset 0 0 0 1px var(--ks-btn-secondary-border-hover);
            }

            &:active {
                background-color: var(--ks-btn-secondary-bg-active);
                box-shadow: none;
            }
        }

        &.kel-button--primary:not(.is-text):not(.is-link).is-disabled {
            background-color: var(--ks-btn-primary-bg-inactive);
            border-color: var(--ks-btn-primary-bg-inactive);
            color: var(--ks-text-inactive);
        }
    }
</style>
