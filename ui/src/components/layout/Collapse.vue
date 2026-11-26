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
