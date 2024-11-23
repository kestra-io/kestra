<template>
    <div data-component="FILENAME_PLACEHOLDER" class="collapse mb-sm-4 mb-md-0">
        <div class="button mb-2">
            <el-button @click="isNavbarVisible = !isNavbarVisible">
                <menu-icon />
            </el-button>
        </div>
        <el-form :inline="true" @submit="prevent" :class="{'d-block': isNavbarVisible}">
            <slot />
        </el-form>
    </div>
</template>

<script>
    import MenuIcon from "vue-material-design-icons/Menu.vue";

    export default {
        components: {MenuIcon},
        data() {
            return {
                isNavbarVisible: false,
            };
        },
        methods: {
            prevent(event) {
                event.preventDefault();
            },
        },
    };
</script>

<style scoped lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    @include res(sm) {
        :deep(.el-form--inline) {
            .el-form-item {
                margin-right: 5px;

                .el-radio-group {
                    margin-right: 5px;
                }

                .el-form-item__content {
                    > *:not(.el-button-group) {
                        width: 200px;
                    }

                    > .el-button-group {
                        max-width: 200px;
                    }
                }
            }
        }
    }

    @include res(xs) {
        :deep(.el-form--inline) {
            .el-form-item {
                display: block;
                margin-right: 0;

                .el-input {
                    margin: 0;
                }

                &:last-child {
                    margin-bottom: 0;
                }
            }
        }
    }

    .collapse {
        > div:first-child {
            display: flex;
            @include res(sm) {
                display: none;
            }

            button {
                margin-left: auto;
            }
        }

        @include res(xs) {
            .el-form--inline {
                display: none;
            }
        }
    }
</style>
