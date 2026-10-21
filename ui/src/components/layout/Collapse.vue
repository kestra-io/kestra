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

<style scoped>
    @media only screen and (min-width: 768px) {
        :deep(.kel-form--inline) {
            .kel-form-item {
                margin-right: 5px;

                .kel-radio-group {
                    margin-right: 5px;
                }

                .kel-form-item__content {
                    & > *:not(.kel-button-group) {
                        width: 200px;
                    }

                    & > .kel-button-group {
                        max-width: 200px;
                    }
                }
            }
        }
    }

    @media only screen and (max-width: 767px) {
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
        /* Bootstrap's sm breakpoint; the query below starts at 768px */
        @media (min-width: 576px) {
            margin-bottom: var(--ks-spacing-5);
        }
        @media only screen and (min-width: 768px) {
            margin-bottom: 0;
        }

        .button {
            margin-bottom: var(--ks-spacing-2);
        }

        & > div:first-child {
            display: flex;
            @media only screen and (min-width: 768px) {
                display: none;
            }

            button {
                margin-left: auto;
            }
        }

        @media only screen and (max-width: 767px) {
            .kel-form--inline {
                display: none;
            }
        }

        .kel-form--inline.open {
            display: block;
        }
    }
</style>
