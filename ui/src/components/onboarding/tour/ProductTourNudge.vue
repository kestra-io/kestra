<template>
    <div v-if="visible && isBlueprints" class="product-tour-nudge">
        <span class="product-tour-nudge__dismiss">
            <KsIconButton :tooltip="t(tk('actions.dismiss'))" placement="top" @click="dismiss">
                <Close />
            </KsIconButton>
        </span>
        <p class="product-tour-nudge__title">
            {{ t(tk("nudge.title")) }}
        </p>
        <RouterLink :to="tourRoute" class="product-tour-nudge__link">
            <span class="product-tour-nudge__play">
                <Play :size="14" />
            </span>
            <span>{{ t(tk("nudge.action")) }}</span>
        </RouterLink>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute} from "vue-router"
    import Play from "vue-material-design-icons/Play.vue"
    import Close from "vue-material-design-icons/Close.vue"

    import {useProductTourNudge} from "./useProductTourEntry"

    const {t} = useI18n()
    const route = useRoute()
    const {visible, tourRoute, tk, dismiss} = useProductTourNudge()

    const isBlueprints = computed(() => String(route.name ?? "").startsWith("blueprints"))
</script>

<style scoped lang="scss">
    .product-tour-nudge {
        position: relative;
        margin: var(--ks-spacing-4) var(--ks-spacing-2) 0;
        padding: var(--ks-spacing-3);
        border: var(--ks-border-width-thin) solid color-mix(in srgb, var(--ks-btn-primary-bg-default) 45%, transparent);
        border-radius: var(--ks-radius-base);
        background: color-mix(in srgb, var(--ks-btn-primary-bg-default) 12%, transparent);
    }

    .product-tour-nudge__dismiss {
        position: absolute;
        top: var(--ks-spacing-1);
        right: var(--ks-spacing-1);
    }

    .product-tour-nudge__title {
        margin: 0 var(--ks-spacing-4) var(--ks-spacing-2) 0;
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);
        font-weight: var(--ks-font-weight-semibold);
    }

    .product-tour-nudge__link {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);
        text-decoration: none;

        &:hover {
            color: var(--ks-text-primary);

            .product-tour-nudge__play {
                background: var(--ks-btn-primary-bg-hover);
            }
        }
    }

    .product-tour-nudge__play {
        display: flex;
        align-items: center;
        justify-content: center;
        width: var(--ks-spacing-5);
        height: var(--ks-spacing-5);
        flex-shrink: 0;
        border-radius: var(--ks-radius-sm);
        background: var(--ks-btn-primary-bg-default);
        color: var(--ks-btn-primary-text);
        transition: background var(--ks-duration-fast) var(--ks-ease-standard);
    }
</style>
