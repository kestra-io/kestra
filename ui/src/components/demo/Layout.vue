<template>
    <EmptyTemplate class="demo-layout">
        <div class="content">
            <img class="artwork" :src="artwork" :alt="image?.alt ?? ''" aria-hidden="true">
            <h2 class="title">
                {{ title }}
            </h2>
            <p class="message">
                <slot name="message" />
            </p>
            <div class="actions">
                <KsButton
                    type="primary"
                    tag="a"
                    target="_blank"
                    :href="`https://kestra.io/demo?utm_source=app&utm_medium=referral&utm_campaign=demo-${type}`"
                >
                    {{ t("demos.contact_sales") }}
                </KsButton>
                <KsButton
                    v-if="isOnline && video?.source"
                    tag="a"
                    target="_blank"
                    :href="video.source"
                >
                    {{ t("demos.watch_the_video") }}
                </KsButton>
            </div>
            <a
                class="learn-more"
                href="#"
                target="_blank"
                rel="noopener"
            >
                {{ t("learn_more") }}
                <ArrowTopRight :size="14" />
            </a>
        </div>
    </EmptyTemplate>
</template>

<script setup lang="ts">
    import {useNetwork} from "@vueuse/core"
    import {useI18n} from "vue-i18n"
    import {KsButton} from "@kestra-io/design-system"
    import ArrowTopRight from "vue-material-design-icons/ArrowTopRight.vue"

    import EmptyTemplate from "../layout/EmptyTemplate.vue"
    import artwork from "../../assets/empty_visuals/Artwork-empty.svg"

    const {isOnline} = useNetwork()
    const {t} = useI18n()

    defineProps<{
        title: string;
        type: string;
        image?: {
            source: string;
            alt: string;
        };
        video?: {
            source: string;
        };
        embed?: boolean;
    }>()
</script>

<style scoped lang="scss">
    .demo-layout {
        padding: 1rem 0 !important;
        margin-top: 0 !important;
        background-image: none !important;
        min-height: 100%;
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .content {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        text-align: left;
        gap: 10px;
        max-width: 360px;
        padding: 0 1.5rem;
    }

    .artwork {
        width: 120px;
        height: 120px;
        display: block;
    }

    .title {
        margin: 0;
        font-size: var(--ks-font-size-md);
        font-weight: 600;
        line-height: 1.35;
        color: var(--ks-content-primary);
    }

    .message {
        margin: 0;
        font-size: var(--ks-font-size-xs);
        line-height: 1.5;
        color: var(--ks-content-secondary);
    }

    .actions {
        display: flex;
        gap: 8px;
        margin-top: 8px;
        justify-content: flex-start;
    }

    .learn-more {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        margin-top: 4px;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-content-secondary);
        text-decoration: none;

        &:hover {
            color: var(--ks-content-link);
        }
    }
</style>
