<template>
    <div class="collapse">
        <div class="button">
            <KsButton :tooltip="$t('toggle menu')" @click="isNavbarVisible = !isNavbarVisible">
                <MenuIcon />
            </KsButton>
        </div>
        <KsForm :inline="true" @submit.prevent :class="{open: isNavbarVisible}">
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
    @use '../../styles/responsive' as *;

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
        // Bootstrap's sm breakpoint; res(sm) starts at 768px
        @media (min-width: 576px) {
            margin-bottom: var(--ks-spacing-5);
        }
        @include res(sm) {
            margin-bottom: 0;
        }

        .button {
            margin-bottom: var(--ks-spacing-2);
        }

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

        .kel-form--inline.open {
            display: block;
        }
    }
</style>
