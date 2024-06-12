<template>
    <span>
        <!-- Valid -->
        <el-button v-if="!errors && !warnings" v-bind="$attrs" :link="link" :size="size" type="default" class="success">
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
                    <el-main>{{ errors.join("\n") }}</el-main>
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
                    <el-main>{{ warnings.join("\n") }}</el-main>
                </el-container>
            </template>
            <el-button v-bind="$attrs" :link="link" :size="size" type="default" class="warning">
                <alert class="text-warning" />
                <span class="text-warning label">{{ $t("warning detected") }}</span>
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
    @import "@kestra-io/ui-libs/src/scss/variables.scss";

    .el-button.el-button--default {
        transition: none;

        &.el-button--small {
            padding: 5px;
            height: fit-content;
        }

        &:hover, &:focus {
            background-color: var(--el-button-bg-color);
        }

        &.success {
            border-color: rgb(var(--bs-success-rgb));
        }

        &:not(.success) span:not(.material-design-icon) {
            margin-left: calc(var(--spacer) / 2);
            font-size: $font-size-sm;
        }

        &.warning {
            border-color: rgb(var(--bs-warning-rgb));
        }

        &.error {
            border-color: rgb(var(--bs-danger-rgb));
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
            background-color: var(--bs-gray-200);
            border-radius: $border-radius-lg $border-radius-lg 0 0;
            font-size: $font-size-sm;
            font-weight: $font-weight-bold;

            html.dark & {
                background-color: var(--bs-gray-500);
            }

            .material-design-icon {
                font-size: 1.5rem;
                margin-right: calc(var(--spacer) / 2);
            }
        }

        .el-main {
            padding: calc(2 * var(--spacer)) $spacer !important;
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