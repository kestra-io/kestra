<template>
    <span>
        <!-- Valid -->
        <el-button v-if="!errors && !warnings &&!infos" v-bind="$attrs" :link="link" :size="size" type="default" class="success">
            <check-circle class="text-success" />
        </el-button>

        <!-- Errors -->
        <el-tooltip
            effect="light"
            v-if="errors"
            popper-class="p-0 bg-transparent"
            :placement="tooltipPlacement"
            :show-arrow="false"
            raw-content
            transition=""
            :persistent="true"
            :hide-after="0"
        >
            <template #content>
                <el-container class="validation-tooltip">
                    <el-header>
                        <alert-circle class="align-middle text-danger" />
                        <span class="align-middle">
                            {{ $t("error detected") }}
                        </span>
                    </el-header>
                    <el-main v-for="error in errors" :key="error">{{ error }}</el-main>
                </el-container>
            </template>
            <el-button v-bind="$attrs" :link="link" :size="size" type="default" class="error">
                <alert-circle class="text-danger" />
                <span class="text-danger label">{{ $t("error detected") }}</span>
            </el-button>
        </el-tooltip>

        <!-- Warnings -->
        <el-tooltip
            effect="light"
            v-if="warnings"
            popper-class="p-0 bg-transparent"
            :placement="tooltipPlacement"
            :show-arrow="false"
            raw-content
            transition=""
            :persistent="true"
            :hide-after="0"
        >
            <template #content>
                <el-container class="validation-tooltip">
                    <el-header>
                        <alert class="align-middle text-warning" />
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
                            {{ info }}<br v-if="index < infos.length - 1">
                        </span>
                    </el-main>
                </el-container>
            </template>
            <el-button v-bind="$attrs" :link="link" :size="size" type="default" class="warning">
                <alert class="text-warning" />
                <span class="text-warning label">{{ $t("warning detected") }}</span>
            </el-button>
        </el-tooltip>

        <!-- Infos -->
        <el-tooltip
            effect="light"
            v-if="infos && !warnings"
            popper-class="p-0 bg-transparent"
            :placement="tooltipPlacement"
            :show-arrow="false"
            raw-content
            transition=""
            :persistent="true"
            :hide-after="0"
        >
            <template #content>
                <el-container class="validation-tooltip">
                    <el-header>
                        <alert class="align-middle text-info" />
                        <span class="align-middle">
                            {{ $t("informative notice") }}
                        </span>
                    </el-header>
                    <el-main>{{ infos.join("<\n") }}</el-main>
                </el-container>
            </template>
            <el-button v-bind="$attrs" :link="link" :size="size" type="default" class="info">
                <alert class="text-info" />
                <span class="text-info label">{{ $t("informative notice") }}</span>
            </el-button>
        </el-tooltip>
    </span>
</template>

<script>
    import CheckCircle from "vue-material-design-icons/CheckCircle.vue";
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue";
    import Alert from "vue-material-design-icons/Alert.vue";

    export default {
        inheritAttrs: false,
        components: {
            CheckCircle,
            AlertCircle,
            Alert
        },
        props: {
            errors: {
                type: Array,
                default: undefined
            },
            warnings: {
                type: Array,
                default: undefined
            },
            infos: {
                type: Array,
                default: undefined
            },
            link: {
                type: Boolean,
                default: false
            },
            size: {
                type: String,
                default: "default"
            },
            tooltipPlacement: {
                type: String,
                default: undefined
            }
        },
        methods: {
            onResize(maxWidth) {
                const buttonLabels = this.$el.querySelectorAll(".el-button span.label");

                buttonLabels.forEach(el => el.classList.remove("d-none"))
                this.$nextTick(() => {
                    if(this.$el.offsetLeft + this.$el.offsetWidth > maxWidth) {
                        buttonLabels.forEach(el => el.classList.add("d-none"))
                    }
                });
            }
        }
    };
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
            border-color: rgb(var(--bs-success-rgb));
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
        border-radius: $border-radius-lg;
        color: $black;

        html.dark & {
            color: white;
        }

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
            padding: 2rem 1rem !important;
            font-family: $font-family-monospace;
            background-color: white;
            white-space: normal;
            border-top: 1px solid var(--bs-gray-300);
            text-wrap: wrap;

            html.dark & {
                color: white;
                background-color: var(--bs-gray-400);
                border-top: 1px solid var(--bs-gray-600);
            }
        }
    }
</style>