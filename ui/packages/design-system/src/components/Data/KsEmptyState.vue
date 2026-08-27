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

            <div v-if="$slots.action || learnMore" class="ks-empty-state__actions">
                <slot name="action" />
                <KsButton
                    v-if="learnMore"
                    tag="a"
                    target="_blank"
                    rel="noopener"
                    :href="learnMore"
                >
                    {{ $t("ks_empty_state.learn_more") }}
                </KsButton>
            </div>
        </div>
    </section>
</template>

<script setup lang="ts">
    import KsButton from "../Basic/KsButton/KsButton.vue"

    defineProps<{
        title?: string;
        description?: string;
        image?: string;
        imageAlt?: string;
        learnMore?: string;
    }>()

    defineSlots<{
        action?(): unknown;
        description?(): unknown;
    }>()
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
    img {
        width: 120px;
        height: 120px;
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
    font-size: var(--ks-font-size-xl);
    line-height: var(--ks-line-height-tight);
    color: var(--ks-text-primary);
    font-weight: var(--ks-font-weight-semibold);
}

.ks-empty-state__description {
    margin: 0;
    width: 100%;
    font-size: var(--ks-font-size-base);
    color: var(--ks-text-secondary);
    line-height: var(--ks-line-height-tight);
}

.ks-empty-state__actions {
    display: flex;
    gap: 0.5rem;
    justify-content: flex-start;

    :deep(.kel-button + .kel-button) {
        margin-left: 0;
    }
}
</style>
