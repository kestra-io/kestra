<template>
    <div v-if="visible" class="tour">
        <RouterLink :to="tourRoute" custom v-slot="{href, navigate}">
            <KsSideBarItem
                class="link"
                :title="$t(translationKey(menuKey))"
                :icon="Play"
                :href="href"
                @click="navigate"
            />
        </RouterLink>
        <span class="dismiss">
            <KsIconButton :tooltip="$t(translationKey('actions.dismiss'))" placement="top" @click="dismiss">
                <Close />
            </KsIconButton>
        </span>
    </div>
</template>

<script setup lang="ts">
    import {RouterLink} from "vue-router"
    import Play from "vue-material-design-icons/Play.vue"
    import Close from "vue-material-design-icons/Close.vue"
    import {useProductTourMenuEntry} from "./useProductTourEntry"

    const {visible, menuKey, tourRoute, translationKey, dismiss} = useProductTourMenuEntry()
</script>

<style scoped lang="scss">
    .tour {
        position: relative;

        &:hover .dismiss,
        &:focus-within .dismiss {
            opacity: 1;
        }

        .link {
            margin: 0;
            --ks-sidebar-item-title-color: currentColor;
            border: var(--ks-border-width-thin) solid color-mix(in srgb, var(--ks-btn-primary-bg-default) 55%, transparent);
            background: color-mix(in srgb, var(--ks-btn-primary-bg-default) 14%, transparent);

            &:hover {
                border-color: var(--ks-btn-primary-bg-default);
                background: color-mix(in srgb, var(--ks-btn-primary-bg-default) 24%, transparent);
            }
        }

        .dismiss {
            position: absolute;
            top: 50%;
            right: var(--ks-spacing-1);
            transform: translateY(-50%);
            opacity: 0;
            transition: opacity var(--ks-duration-fast) var(--ks-ease-standard);
        }
    }
</style>