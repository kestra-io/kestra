<template>
    <span ref="rootContainer">
        <!-- Valid -->
        <KsCodeStatus
            v-if="!errors && !warnings && !infos"
            status="valid"
            :iconOnly="iconOnly"
            :label="$t('valid')"
        />

        <!-- Errors -->
        <KsTooltip
            v-if="errors"
            popperClass="validation-error-tooltip-popper"
            :placement="tooltipPlacement"
            :showArrow="false"
            rawContent
        >
            <template #content>
                <KsContainer class="validation-tooltip">
                    <KsHeader>
                        <AlertCircle class="validation-error-icon error" />
                        <span class="validation-error-label">
                            {{ $t("error detected") }}
                        </span>
                    </KsHeader>
                    <KsMain v-for="error in errors" :key="error">{{ error }}</KsMain>
                </KsContainer>
            </template>
            <button
                v-if="activatable"
                type="button"
                class="error-status-action"
                data-test="validation-error-next"
                :aria-label="$t('flow_editor_stats.errors.action', {count: errors.length})"
                @click="$emit('activate')"
            >
                <KsCodeStatus
                    status="error"
                    :iconOnly="iconOnly"
                    :label="$t('flow_editor_stats.errors.label', {count: errors.length})"
                />
            </button>
            <KsCodeStatus
                v-else
                status="error"
                :iconOnly="iconOnly"
                :label="$t('flow_editor_stats.errors.label', {count: errors.length})"
            />
        </KsTooltip>

        <!-- Warnings -->
        <KsTooltip
            v-if="warnings"
            popperClass="validation-error-tooltip-popper"
            :placement="tooltipPlacement"
            :showArrow="false"
            rawContent
        >
            <template #content>
                <KsContainer class="validation-tooltip">
                    <KsHeader>
                        <Alert class="validation-error-icon warning" />
                        <span class="validation-error-label">
                            {{ $t("warning detected") }}
                        </span>
                    </KsHeader>
                    <KsMain>
                        <span v-for="(warning, index) in warnings" :key="index">
                            {{ warning }}<br v-if="index < warnings.length - 1">
                        </span>
                        <br v-if="infos && infos.length > 0">
                        <span v-for="(info, index) in infos" :key="index">
                            {{ info }}<br v-if="index < (infos?.length ?? 0) - 1">
                        </span>
                    </KsMain>
                </KsContainer>
            </template>
            <KsCodeStatus
                status="warning"
                :iconOnly="iconOnly"
                :label="$t('warning detected')"
            />
        </KsTooltip>

        <!-- Infos -->
        <KsTooltip
            v-if="infos && !warnings"
            popperClass="validation-error-tooltip-popper"
            :placement="tooltipPlacement"
            :showArrow="false"
            rawContent
        >
            <template #content>
                <KsContainer class="validation-tooltip">
                    <KsHeader>
                        <Alert class="validation-error-icon info" />
                        <span class="validation-error-label">
                            {{ $t("informative notice") }}
                        </span>
                    </KsHeader>
                    <KsMain>{{ infos.join("<\n") }}</KsMain>
                </KsContainer>
            </template>
            <KsCodeStatus
                status="info"
                :iconOnly="iconOnly"
                :label="$t('informative notice')"
            />
        </KsTooltip>
    </span>
</template>

<script setup lang="ts">
    import {nextTick, ref} from "vue"
    import type {KsTooltipProps} from "@kestra-io/design-system"
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue"
    import Alert from "vue-material-design-icons/Alert.vue"

    defineOptions({
        inheritAttrs: false,
    })

    defineProps<{
        errors?: string[] | undefined;
        warnings?: string[] | undefined;
        infos?: string[] | undefined;
        link?: boolean;
        size?: "default" | "small";
        tooltipPlacement?: KsTooltipProps["placement"];
        iconOnly?: boolean;
        activatable?: boolean;
    }>()

    defineEmits<{
        activate: [];
    }>()

    const rootContainer = ref<HTMLSpanElement>()

    function onResize(maxWidth: number) {
        if(rootContainer.value === undefined) {
            return
        }
        const buttonLabels = rootContainer.value.querySelectorAll(".kel-button span.label")

        buttonLabels.forEach(el => el.classList.remove("validation-error-label-hidden"))
        nextTick(() => {
            if(rootContainer.value && rootContainer.value.offsetLeft + rootContainer.value.offsetWidth > maxWidth) {
                buttonLabels.forEach(el => el.classList.add("validation-error-label-hidden"))
            }
        })
    }

    defineExpose({
        onResize,
    })
</script>

<style scoped>
    .validation-tooltip {
        padding: 0;
        width: fit-content;
        min-width: 20vw;
        max-width: 50vw;
        max-height: 500px;
        border-radius: 0.5rem;
        color: var(--ks-text-primary);
        overflow-y: auto;
        display:flex;
        flex-direction: column;

        & > * {
            height: fit-content;
            margin: 0;
        }

        .kel-header {
            padding: 1rem;
            background-color: var(--ks-bg-surface);
            border-radius: 0.5rem 0.5rem 0 0;
            font-size: var(--ks-font-size-sm);
            font-weight: 700;

            .material-design-icon {
                font-size: var(--ks-font-size-xl);
                margin-right: .5rem;
            }
        }

        .kel-main {
            padding: 1.5rem 1rem !important;
            font-family: "JetBrains Mono", monospace;
            background-color: var(--ks-bg-surface);
            white-space: pre-wrap;
            border-top: 1px solid var(--ks-border-default);
            word-break: break-word;
            min-height: fit-content;
            color: var(--ks-text-primary);
        }
    }

    .warning {
        color: var(--ks-status-warning);
    }

    .error {
        color: var(--ks-status-error);
    }

    .info {
        color: var(--ks-status-info);
    }

    .error-status-action {
        display: inline-flex;
        padding: 0;
        border: none;
        background: none;
        border-radius: var(--ks-radius-sm);
        cursor: pointer;

        &:hover {
            background: var(--ks-bg-error);
        }

        &:focus-visible {
            outline: var(--ks-border-width-thin) solid var(--ks-border-focus);
            outline-offset: var(--ks-spacing-1);
        }
    }
    .validation-error-icon,
    .validation-error-label {
        vertical-align: middle;
    }
    :global(.validation-error-tooltip-popper) {
        padding: 0 !important;
        background-color: transparent !important;
    }
    :global(.validation-error-label-hidden) {
        display: none;
    }
</style>
