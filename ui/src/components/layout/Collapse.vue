<template>
    <div class="collapse mb-sm-4 mb-md-0">
        <div class="button mb-2">
            <KsButton :tooltip="$t('toggle menu')" @click="isNavbarVisible = !isNavbarVisible">
                <MenuIcon />
            </KsButton>
        </div>
        <KsForm :inline="true" @submit.prevent :class="{'d-block': isNavbarVisible}">
            <slot />
        </KsForm>
    </div>
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import MenuIcon from "vue-material-design-icons/Menu.vue"

    const isNavbarVisible = ref(false)
</script>

<style scoped lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    @include res(sm) {
        :deep(.kel-form--inline) {
            .kel-form-item {
                margin-right: 5px;

                .kel-radio-group {
                    margin-right: 5px;
                }

                .kel-form-item__content {
                    > *:not(.kel-button-group) {
                        width: 200px;
                    }

                    > .kel-button-group {
                        max-width: 200px;
                    }
                }
            }
        }
    }

    @include res(xs) {
        :deep(.kel-form--inline) {
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
            .kel-form--inline {
                display: none;
            }
        }
    }
</style>
