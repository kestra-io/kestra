<template>
    <!--
        Blueprints are read by people who have not built anything yet, and the tabs rail has room to
        spare, so the tour is offered here as well. It stays until it is closed here.
    -->
    <div v-if="visible && isBlueprints" class="product-tour-nudge">
        <KsTooltip :content="t('onboarding.tour.actions.dismiss')">
            <KsButton
                link
                size="small"
                class="product-tour-nudge__dismiss"
                :icon="Close"
                :aria-label="t('onboarding.tour.actions.dismiss')"
                @click="dismiss"
            />
        </KsTooltip>
        <p class="product-tour-nudge__title">
            {{ t("onboarding.tour.nudge.title") }}
        </p>
        <RouterLink :to="tourRoute" class="product-tour-nudge__link">
            <span class="product-tour-nudge__play">
                <Play :size="14" />
            </span>
            <span>{{ t("onboarding.tour.nudge.action") }}</span>
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
    const {visible, tourRoute, dismiss} = useProductTourNudge()

    const isBlueprints = computed(() => String(route.name ?? "").startsWith("blueprints"))
</script>

<style scoped lang="scss">
    .product-tour-nudge {
        // The tour's purple, as in the left menu entry and on the Copilot page.
        --product-tour-accent: #8b5cf6;
        position: relative;
        margin: var(--ks-spacing-4) var(--ks-spacing-2) 0;
        padding: 0.75rem;
        border: 1px solid color-mix(in srgb, var(--product-tour-accent) 45%, transparent);
        border-radius: var(--ks-radius-base);
        background: color-mix(in srgb, var(--product-tour-accent) 12%, transparent);
    }

    .product-tour-nudge__dismiss {
        position: absolute;
        top: 2px;
        right: 2px;
    }

    .product-tour-nudge__title {
        margin: 0 1rem 0.5rem 0;
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
    }

    // Deliberately not the link colour: this opens the tour rather than navigating somewhere, and
    // the accent is dark enough on this background to be hard to read.
    .product-tour-nudge__link {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);
        text-decoration: none;

        &:hover {
            color: var(--ks-text-primary);

            .product-tour-nudge__play {
                background: color-mix(in srgb, var(--product-tour-accent) 80%, white);
            }
        }
    }

    .product-tour-nudge__play {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 22px;
        height: 22px;
        flex-shrink: 0;
        border-radius: var(--ks-radius-sm);
        background: var(--product-tour-accent);
        color: #0d1117;
        transition: background 0.15s ease;
    }
</style>
