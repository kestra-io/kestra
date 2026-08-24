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

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        tooltip?: string
        placement?: string
        ariaLabel?: string
        disabled?: boolean
        to?: string | Record<string, unknown>
        replace?: boolean
        filled?: boolean
    }>(), {
        tooltip: "",
        placement: "left",
        ariaLabel: "",
        disabled: false,
        to: undefined,
        replace: false,
        filled: false,
    })

    defineSlots<{
        default?(): unknown
    }>()

    const attrs = useAttrs()
    const buttonAttrs = computed(() => ({
        ...attrs,
        class: ["ks-icon-button", {"ks-icon-button--filled": props.filled}, attrs.class],
    }))

    const buttonTag = computed(() => (props.to ? "router-link" : undefined))
    const nativeType = computed(() => (props.to ? undefined : "button" as const))
</script>

<style scoped lang="scss">
    .ks-icon-button {
        color: var(--ks-text-primary);
        width: 24px;
        height: 24px;
        min-width: 24px;
        border-radius: var(--kel-border-radius-base);
        text-align: center;
        display: inline-flex;
        justify-content: center;
        align-items: center;
        padding: 0;
        cursor: pointer;

        :deep(.material-design-icon),
        :deep(.material-design-icon > .material-design-icon__svg) {
            width: var(--ks-icon-size-sm);
            height: var(--ks-icon-size-sm);
        }

        /* vue-material-design-icons nudges its SVG down by 0.125em (baseline alignment meant for
           inline-with-text use), which reads as off-centre in an icon button — neutralise it. */
        :deep(.material-design-icon > .material-design-icon__svg) {
            bottom: 0;
            left: 50%;
            transform: translateX(-50%);
        }
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
