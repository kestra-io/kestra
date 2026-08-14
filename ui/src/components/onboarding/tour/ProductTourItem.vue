<template>
    <div v-if="visible" class="product-tour-entry">
        <RouterLink :to="tourRoute" class="product-tour-item">
            <span class="product-tour-play">
                <Play :size="18" />
            </span>
            <span class="product-tour-label">
                {{ t(tk(menuKey)) }}
            </span>
        </RouterLink>
        <span class="product-tour-dismiss">
            <KsIconButton :tooltip="t(tk('actions.dismiss'))" placement="top" @click="dismiss">
                <Close />
            </KsIconButton>
        </span>
    </div>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import Play from "vue-material-design-icons/Play.vue"
    import Close from "vue-material-design-icons/Close.vue"

    import {useProductTourMenuEntry} from "./useProductTourEntry"

    const {t} = useI18n()
    const {visible, menuKey, tourRoute, tk, dismiss} = useProductTourMenuEntry()
</script>

<style scoped lang="scss">
    .product-tour-entry {
        position: relative;

        &:hover .product-tour-dismiss {
            opacity: 1;
        }
    }

    .product-tour-dismiss {
        position: absolute;
        top: var(--ks-spacing-1);
        right: var(--ks-spacing-1);
        opacity: 0;
        transition: opacity var(--ks-duration-fast) var(--ks-ease-standard);
    }

    .product-tour-item {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border: var(--ks-border-width-thin) solid color-mix(in srgb, var(--ks-btn-primary-bg-default) 55%, transparent);
        border-radius: var(--ks-radius-base);
        background: color-mix(in srgb, var(--ks-btn-primary-bg-default) 14%, transparent);
        color: var(--ks-text-primary);
        text-decoration: none;
        transition: background var(--ks-duration-fast) var(--ks-ease-standard),
            border-color var(--ks-duration-fast) var(--ks-ease-standard);

        &:hover {
            border-color: var(--ks-btn-primary-bg-default);
            background: color-mix(in srgb, var(--ks-btn-primary-bg-default) 24%, transparent);
        }
    }

    .product-tour-play {
        display: flex;
        align-items: center;
        justify-content: center;
        width: var(--ks-spacing-6);
        height: var(--ks-spacing-6);
        flex-shrink: 0;
        border-radius: var(--ks-radius-sm);
        background: var(--ks-btn-primary-bg-default);
        color: var(--ks-btn-primary-text);
    }

    .product-tour-label {
        font-size: var(--ks-font-size-md);
        font-weight: var(--ks-font-weight-semibold);
    }
</style>
