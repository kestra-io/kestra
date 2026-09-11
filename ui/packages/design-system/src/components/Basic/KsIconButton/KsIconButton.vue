<template>
    <KsTooltip
        v-if="tooltip"
        :content="tooltip"
        :rawContent="true"
        v-bind="placement ? {placement} : {}"
        :enterable="false"
    >
        <KsButton
            v-bind="buttonAttrs"
            :disabled="disabled"
            :aria-label="ariaLabel || tooltip"
            :tag="buttonTag"
            :to="disabled ? undefined : to"
            :replace="replace"
            :nativeType="nativeType"
        >
            <slot />
        </KsButton>
    </KsTooltip>
    <KsButton
        v-else
        v-bind="buttonAttrs"
        :disabled="disabled"
        :aria-label="ariaLabel"
        :tag="buttonTag"
        :to="disabled ? undefined : to"
        :replace="replace"
        :nativeType="nativeType"
    >
        <slot />
    </KsButton>
</template>

<script setup lang="ts">
    import {computed, useAttrs} from "vue"
    import KsButton from "../KsButton/KsButton.vue"
    import KsTooltip from "../../Feedback/KsTooltip.vue"

    type IconSize = "xs" | "sm" | "base" | "lg" | "xl"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        tooltip?: string
        placement?: string
        ariaLabel?: string
        disabled?: boolean
        to?: string | Record<string, unknown>
        replace?: boolean
        filled?: boolean
        size?: IconSize
    }>(), {
        tooltip: "",
        placement: "left",
        ariaLabel: "",
        disabled: false,
        to: undefined,
        replace: false,
        filled: false,
        size: "sm",
    })

    defineSlots<{
        default?(): unknown
    }>()

    const attrs = useAttrs()
    const buttonAttrs = computed(() => ({
        ...attrs,
        class: [
            "ks-icon-button",
            `ks-icon-button--${props.size}`,
            {"ks-icon-button--filled": props.filled},
            attrs.class,
        ],
    }))

    const buttonTag = computed(() => (props.to ? "router-link" : undefined))
    const nativeType = computed(() => (props.to ? undefined : "button" as const))
</script>

<style scoped lang="scss">
    .ks-icon-button {
        --ks-icon-button-glyph: var(--ks-icon-size-sm);

        color: var(--ks-text-primary);
        width: calc(var(--ks-icon-button-glyph) + var(--ks-spacing-2));
        height: calc(var(--ks-icon-button-glyph) + var(--ks-spacing-2));
        min-width: calc(var(--ks-icon-button-glyph) + var(--ks-spacing-2));
        border-radius: var(--kel-border-radius-base);
        text-align: center;
        display: inline-flex;
        justify-content: center;
        align-items: center;
        padding: 0;
        cursor: pointer;

        :deep(.material-design-icon),
        :deep(.material-design-icon > .material-design-icon__svg) {
            width: var(--ks-icon-button-glyph);
            height: var(--ks-icon-button-glyph);
        }
    }

    .ks-icon-button--xs {
        --ks-icon-button-glyph: var(--ks-icon-size-xs);
    }

    .ks-icon-button--base {
        --ks-icon-button-glyph: var(--ks-icon-size-base);
    }

    .ks-icon-button--lg {
        --ks-icon-button-glyph: var(--ks-icon-size-lg);
    }

    .ks-icon-button--xl {
        --ks-icon-button-glyph: var(--ks-icon-size-xl);
    }

    .ks-icon-button:not(.ks-icon-button--filled) {
        background-color: transparent;
        border: none;
        box-shadow: none;

        &:hover {
            color: var(--ks-text-primary);
            background-color: var(--ks-bg-tag);
        }
    }
</style>
