<template>
    <div v-if="visible" class="product-tour-entry">
        <RouterLink :to="tourRoute" class="product-tour-item">
            <span class="product-tour-play">
                <Play :size="18" />
            </span>
            <span class="product-tour-label">
                {{ t("onboarding.tour.menu") }}
            </span>
        </RouterLink>
        <!-- Without this, the entry would only go away by taking the tour to the end. -->
        <KsTooltip :content="t('onboarding.tour.actions.dismiss')">
            <KsButton
                link
                size="small"
                class="product-tour-dismiss"
                :icon="Close"
                :aria-label="t('onboarding.tour.actions.dismiss')"
                @click="dismiss"
            />
        </KsTooltip>
    </div>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import Play from "vue-material-design-icons/Play.vue"
    import Close from "vue-material-design-icons/Close.vue"

    import {useProductTourMenuEntry} from "./useProductTourEntry"

    const {t} = useI18n()
    const {visible, tourRoute, dismiss} = useProductTourMenuEntry()
</script>

<style scoped lang="scss">
    .product-tour-entry {
        // Same purple as the tour's card on the Copilot page, so both entries look like one feature
        // rather than another primary action.
        --product-tour-accent: #8b5cf6;
        position: relative;

        &:hover .product-tour-dismiss {
            opacity: 1;
        }
    }

    .product-tour-dismiss {
        position: absolute;
        top: 2px;
        right: 2px;
        opacity: 0;
        transition: opacity 0.15s ease;
    }

    .product-tour-item {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.625rem 0.75rem;
        border: 1px solid color-mix(in srgb, var(--product-tour-accent) 55%, transparent);
        border-radius: var(--ks-radius-base);
        background: color-mix(in srgb, var(--product-tour-accent) 14%, transparent);
        color: var(--ks-text-primary);
        text-decoration: none;
        transition: background 0.15s ease, border-color 0.15s ease;

        &:hover {
            border-color: var(--product-tour-accent);
            background: color-mix(in srgb, var(--product-tour-accent) 24%, transparent);
        }
    }

    .product-tour-play {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 30px;
        height: 30px;
        flex-shrink: 0;
        border-radius: var(--ks-radius-sm);
        background: var(--product-tour-accent);
        color: #0d1117;
    }

    .product-tour-label {
        font-size: var(--ks-font-size-md, 0.875rem);
        font-weight: 600;
    }
</style>
