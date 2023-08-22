<template>
    <el-tooltip popper-class="p-0 bg-transparent" :placement="tooltipPlacement" :show-arrow="false" :disabled="!error" raw-content transition="" :persistent="false">
        <template #content>
            <el-container class="error-tooltip">
                <el-header>
                    <AlertCircle class="align-middle text-danger" />
                    <span class="align-middle">
                        {{ $t("error detected") }}
                    </span>
                </el-header>
                <el-main>{{ error }}</el-main>
            </el-container>
        </template>
        <el-button v-bind="$attrs" :link="link" :size="size" type="default">
            <component :class="'text-' + (error ? 'danger' : 'success')" :is="error ? AlertCircle : CheckCircle" />
            <span v-if="error" class="text-danger">{{ $t("error detected") }}</span>
        </el-button>
    </el-tooltip>
</template>

<script setup>
    import CheckCircle from "vue-material-design-icons/CheckCircle.vue";
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue";
</script>

<script>
    export default {
        inheritAttrs: false,
        props: {
            error: {
                type: String,
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

        &:has(.material-design-icon.text-success) {
            border-color: rgb(var(--bs-success-rgb));
        }
        &:has(.material-design-icon.text-danger) {
            border-color: rgb(var(--bs-danger-rgb));

            span.text-danger:not(.material-design-icon) {
                margin-left: calc(var(--spacer) / 2);
                font-size: $font-size-sm;
            }
        }
    }

    .error-tooltip {
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
            border-bottom: 1px solid var(--bs-gray-300);
            border-radius: $border-radius-lg $border-radius-lg 0 0;
            font-size: $font-size-sm;
            font-weight: $font-weight-bold;

            html.dark & {
                background-color: var(--bs-gray-500);
                border-bottom: 1px solid var(--bs-gray-600);
            }

            .material-design-icon {
                font-size: 1.5rem;
                margin-right: calc(var(--spacer) / 2);
            }
        }

        .el-main {
            padding: calc(2 * var(--spacer)) $spacer !important;
            border-radius: 0 0 $border-radius-lg $border-radius-lg;
            font-family: $font-family-monospace;
            background-color: white;

            html.dark & {
                color: white;
                background-color: var(--bs-gray-400);
            }
        }
    }
</style>