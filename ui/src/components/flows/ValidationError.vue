<template>
    <span ref="rootContainer">
        <!-- Valid -->
        <KsButton v-if="!errors && !warnings && !infos" v-bind="$attrs" :link="link" :size="size" type="default" class="success square" disabled>
            <CheckBoldIcon class="success" />
        </KsButton>

        <!-- Errors -->
        <KsTooltip
            v-if="errors"
            popperClass="p-0 bg-transparent"
            :placement="tooltipPlacement"
            :showArrow="false"
            rawContent
        >
            <template #content>
                <KsContainer class="validation-tooltip">
                    <KsHeader>
                        <AlertCircle class="align-middle error" />
                        <span class="align-middle">
                            {{ $t("error detected") }}
                        </span>
                    </KsHeader>
                    <KsMain v-for="error in errors" :key="error">{{ error }}</KsMain>
                </KsContainer>
            </template>
            <KsButton v-bind="$attrs" :link="link" :size="size" type="default" class="error square">
                <AlertCircle class="error" />
            </KsButton>
        </KsTooltip>

        <!-- Warnings -->
        <KsTooltip
            v-if="warnings"
            popperClass="p-0 bg-transparent"
            :placement="tooltipPlacement"
            :showArrow="false"
            rawContent
        >
            <template #content>
                <KsContainer class="validation-tooltip">
                    <KsHeader>
                        <Alert class="align-middle warning" />
                        <span class="align-middle">
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
            <KsButton v-bind="$attrs" :link="link" :size="size" type="default" class="warning square">
                <Alert class="warning" />
            </KsButton>
        </KsTooltip>

        <!-- Infos -->
        <KsTooltip
            v-if="infos && !warnings"
            popperClass="p-0 bg-transparent"
            :placement="tooltipPlacement"
            :showArrow="false"
            rawContent
        >
            <template #content>
                <KsContainer class="validation-tooltip">
                    <KsHeader>
                        <Alert class="align-middle info" />
                        <span class="align-middle">
                            {{ $t("informative notice") }}
                        </span>
                    </KsHeader>
                    <KsMain>{{ infos.join("<\n") }}</KsMain>
                </KsContainer>
            </template>
            <KsButton v-bind="$attrs" :link="link" :size="size" type="default" class="info">
                <Alert class="info" />
                <span class="info label">{{ $t("informative notice") }}</span>
            </KsButton>
        </KsTooltip>
    </span>
</template>

<script setup lang="ts">
    import {nextTick, ref} from "vue"
    import CheckBoldIcon from "vue-material-design-icons/CheckBold.vue"
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
        tooltipPlacement?: string;
    }>()

    const rootContainer = ref<HTMLSpanElement>()

    function onResize(maxWidth: number) {
        if(rootContainer.value === undefined) {
            return
        }
        const buttonLabels = rootContainer.value.querySelectorAll(".kel-button span.label")

        buttonLabels.forEach(el => el.classList.remove("d-none"))
        nextTick(() => {
            if(rootContainer.value && rootContainer.value.offsetLeft + rootContainer.value.offsetWidth > maxWidth) {
                buttonLabels.forEach(el => el.classList.add("d-none"))
            }
        })
    }

    defineExpose({
        onResize,
    })

</script>

<style scoped lang="scss">

    .kel-button.kel-button--default {
        transition: none;

        &.kel-button--small {
            padding: 5px;
            height: fit-content;
        }

        &:hover, &:focus {
            background-color: var(--ks-button-background-secondary);
        }

        &.success {
            cursor: default;
            border-color: var(--ks-border-success);

            &.is-disabled,
            &.is-disabled:hover,
            &.is-disabled:focus {
                opacity: 1;
                background-color: transparent;
                border-color: var(--ks-border-success);
            }
        }

        &:not(.success) span:not(.material-design-icon) {
            margin-left: .5rem;
            font-size: var(--ks-font-size-sm);
        }

        &.warning {
            border-color: var(--ks-border-warning);
        }

        &.error {
            border-color: var(--ks-border-error);
        }
    }

    .validation-tooltip {
        padding: 0;
        width: fit-content;
        min-width: 20vw;
        max-width: 50vw;
        max-height: 500px;
        border-radius: 0.5rem;
        color: var(--ks-content-primary);
        overflow-y: auto;

        > * {
            height: fit-content;
            margin: 0;
        }

        .kel-header {
            padding: 1rem;
            background-color: var(--ks-background-table-header);
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
            font-family: "Source Code Pro", monospace;
            background-color: var(--ks-background-card);
            white-space: normal;
            border-top: 1px solid var(--ks-border-primary);
            text-wrap: wrap;
            min-height: fit-content;
            color: var(--ks-content-primary);
        }
    }

    .square {
        width: 32px;
        height: 32px;
    }

    .success {
        color: var(--ks-content-success);
    }

    .warning {
        color: var(--ks-content-warning);
    }

    .error {
        color: var(--ks-content-error);
    }

    .info {
        color: var(--ks-content-info);
    }
</style>
