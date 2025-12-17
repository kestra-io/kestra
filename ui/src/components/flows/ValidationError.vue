<template>
    <span ref="rootContainer">
        <!-- Valid -->
        <el-button v-if="!errors && !warnings && !infos" v-bind="$attrs" :link="link" :size="size" type="default" class="success square" disabled>
            <CheckBoldIcon class="text-success" />
        </el-button>

        <!-- Errors -->
        <el-tooltip
            effect="light"
            v-if="errors"
            popperClass="p-0 bg-transparent"
            :placement="tooltipPlacement"
            :showArrow="false"
            rawContent
            transition=""
            :persistent="true"
            :hideAfter="0"
        >
            <template #content>
                <el-container class="validation-tooltip">
                    <el-header>
                        <AlertCircle class="align-middle text-danger" />
                        <span class="align-middle">
                            {{ $t("error detected") }}
                        </span>
                    </el-header>
                    <el-main v-for="error in errors" :key="error">{{ error }}</el-main>
                </el-container>
            </template>
            <el-button v-bind="$attrs" :link="link" :size="size" type="default" class="error square">
                <AlertCircle class="text-danger" />
            </el-button>
        </el-tooltip>

        <!-- Warnings -->
        <el-tooltip
            effect="light"
            v-if="warnings"
            popperClass="p-0 bg-transparent"
            :placement="tooltipPlacement"
            :showArrow="false"
            rawContent
            transition=""
            :persistent="true"
            :hideAfter="0"
        >
            <template #content>
                <el-container class="validation-tooltip">
                    <el-header>
                        <Alert class="align-middle text-warning" />
                        <span class="align-middle">
                            {{ $t("warning detected") }}
                        </span>
                    </el-header>
                    <el-main>
                        <span v-for="(warning, index) in warnings" :key="index">
                            {{ warning }}<br v-if="index < warnings.length - 1">
                        </span>
                        <br v-if="infos && infos.length > 0">
                        <span v-for="(info, index) in infos" :key="index">
                            {{ info }}<br v-if="index < (infos?.length ?? 0) - 1">
                        </span>
                    </el-main>
                </el-container>
            </template>
            <el-button v-bind="$attrs" :link="link" :size="size" type="default" class="warning square">
                <Alert class="text-warning" />
            </el-button>
        </el-tooltip>

        <!-- Infos -->
        <el-tooltip
            effect="light"
            v-if="infos && !warnings"
            popperClass="p-0 bg-transparent"
            :placement="tooltipPlacement"
            :showArrow="false"
            rawContent
            transition=""
            :persistent="true"
            :hideAfter="0"
        >
            <template #content>
                <el-container class="validation-tooltip">
                    <el-header>
                        <Alert class="align-middle text-info" />
                        <span class="align-middle">
                            {{ $t("informative notice") }}
                        </span>
                    </el-header>
                    <el-main>{{ infos.join("<\n") }}</el-main>
                </el-container>
            </template>
            <el-button v-bind="$attrs" :link="link" :size="size" type="default" class="info">
                <Alert class="text-info" />
                <span class="text-info label">{{ $t("informative notice") }}</span>
            </el-button>
        </el-tooltip>
    </span>
</template>

<script setup lang="ts">
    import {nextTick, ref} from "vue";
    import CheckBoldIcon from "vue-material-design-icons/CheckBold.vue";
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue";
    import Alert from "vue-material-design-icons/Alert.vue";

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
            return;
        }
        const buttonLabels = rootContainer.value.querySelectorAll(".el-button span.label");

        buttonLabels.forEach(el => el.classList.remove("d-none"))
        nextTick(() => {
            if(rootContainer.value && rootContainer.value.offsetLeft + rootContainer.value.offsetWidth > maxWidth) {
                buttonLabels.forEach(el => el.classList.add("d-none"))
            }
        });
    }

    defineExpose({
        onResize
    })

</script>

<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    .el-button.el-button--default {
        transition: none;

        &.el-button--small {
            padding: 5px;
            height: fit-content;
        }

        &:hover, &:focus {
            background-color: var(--ks-button-background-secondary);
        }

        &.success {
            cursor: default;
            border-color: var(--ks-border-success);
        }

        &:not(.success) span:not(.material-design-icon) {
            margin-left: .5rem;
            font-size: $font-size-sm;
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
        border-radius: $border-radius-lg;
        color: var(--ks-content-primary);
        overflow-y: auto;

        > * {
            height: fit-content;
            margin: 0;
        }

        .el-header {
            padding: $spacer;
            background-color: var(--ks-background-table-header);
            border-radius: $border-radius-lg $border-radius-lg 0 0;
            font-size: $font-size-sm;
            font-weight: $font-weight-bold;

            .material-design-icon {
                font-size: 1.5rem;
                margin-right: .5rem;
            }
        }

        .el-main {
            padding: 1.5rem 1rem !important;
            font-family: $font-family-monospace;
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
</style>