<template>
    <section class="ks-empty-state">
        <div class="ks-empty-state__inner">
            <div v-if="image" class="ks-empty-state__artwork" aria-hidden="true">
                <img :src="image" :alt="imageAlt ?? ''">
            </div>

            <div v-if="title || description || $slots.description" class="ks-empty-state__text">
                <h2 v-if="title" class="ks-empty-state__title">{{ title }}</h2>
                <p v-if="description || $slots.description" class="ks-empty-state__description">
                    <slot name="description">
                        <!-- eslint-disable-next-line vue/no-v-html -->
                        <span v-html="description" />
                    </slot>
                </p>
            </div>

            <div v-if="$slots.action || (isOnline && video)" class="ks-empty-state__actions">
                <slot name="action" />
                <KsButton
                    v-if="isOnline && video"
                    tag="a"
                    target="_blank"
                    :href="video"
                >
                    {{ t("ks_empty_state.watch_the_video") }}
                </KsButton>
            </div>

            <a
                v-if="learnMore"
                class="ks-empty-state__learn-more"
                :href="learnMore"
                target="_blank"
                rel="noopener"
            >
                {{ t("ks_empty_state.learn_more") }}
                <ArrowTopRight :size="14" />
            </a>
        </div>
    </section>
</template>

<script setup lang="ts">
    import {useNetwork} from "@vueuse/core"
    import {useI18n} from "vue-i18n"
    import ArrowTopRight from "vue-material-design-icons/ArrowTopRight.vue"
    import KsButton from "../Basic/KsButton/KsButton.vue"

    defineProps<{
        title?: string;
        description?: string;
        image?: string;
        imageAlt?: string;
        video?: string;
        learnMore?: string;
    }>()

    defineSlots<{
        action?(): unknown;
        description?(): unknown;
    }>()

    const {isOnline} = useNetwork()
    const {t} = useI18n({useScope: "global"})
</script>

<style lang="scss" scoped>
.ks-empty-state {
    width: 100%;
    height: 100%;
    min-height: 70vh;
    padding: 3rem 0;
    display: flex;
    align-items: center;
    justify-content: center;
    text-align: left;
}

.ks-empty-state__inner {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    max-width: 370px;
    gap: 21px;
    padding: 0 0.5rem;
}

.ks-empty-state__artwork {
    width: 104px;
    height: 104px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--ks-bg-surface);
    border-radius: 12px;

    img {
        width: 80px;
        height: 80px;
        display: block;
    }
}

.ks-empty-state__text {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.ks-empty-state__title {
    margin: 0;
    font-size: 18px;
    line-height: 22px;
    color: var(--ks-text-primary);
    font-weight: var(--ks-font-weight-semibold);
}

.ks-empty-state__description {
    margin: 0;
    width: 100%;
    font-size: 14px;
    color: var(--ks-text-secondary);
    line-height: 18px;
}

.ks-empty-state__actions {
    display: flex;
    gap: 0.5rem;
    justify-content: flex-start;

    :deep(.kel-button + .kel-button) {
        margin-left: 0;
    }
}

.ks-empty-state__learn-more {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);
    text-decoration: none;

    &:hover {
        color: var(--ks-text-link);
    }
}
</style>
