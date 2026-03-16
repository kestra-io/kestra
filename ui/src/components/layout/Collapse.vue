<template>
    <div class="collapse mb-sm-4 mb-md-0">
        <div class="button mb-2">
            <ks-button @click="isNavbarVisible = !isNavbarVisible">
                <MenuIcon />
            </ks-button>
        </div>
        <ks-form :inline="true" @submit.prevent :class="{'d-block': isNavbarVisible}">
            <slot />
        </ks-form>
    </div>
</template>

<script setup lang="ts">
    import {ref} from "vue";
    import MenuIcon from "vue-material-design-icons/Menu.vue";

    const isNavbarVisible = ref(false);
</script>

<style scoped lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    @include res(sm) {
        :deep(.el-form--inline) {
            .kel-form-item {
                margin-right: 5px;

                .el-radio-group {
                    margin-right: 5px;
                }

                .kel-form-item__content {
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
            .kel-form-item {
                display: block;
                margin-right: 0;

                .kel-input {
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
