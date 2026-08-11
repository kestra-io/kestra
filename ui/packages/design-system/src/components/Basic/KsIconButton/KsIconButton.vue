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
            class="ks-icon-button"
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
        class="ks-icon-button"
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
    }>(), {
        tooltip: "",
        placement: "left",
        ariaLabel: "",
        disabled: false,
        to: undefined,
        replace: false,
    })

    defineSlots<{
        default?(): unknown
    }>()

    const attrs = useAttrs()
    const buttonAttrs = computed(() => ({
        ...attrs,
        class: [attrs.class],
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
        background-color: transparent;
        border: none;
        box-shadow: none;
        padding: 0;
        cursor: pointer;

        &:hover {
            color: var(--ks-text-primary);
            background-color: var(--ks-bg-tag);
        }

        :deep(.material-design-icon__svg) {
            width: 16px;
            height: 16px;
            transform: translateY(1px) translateX(-0.5px);
        }
    }
</style>
