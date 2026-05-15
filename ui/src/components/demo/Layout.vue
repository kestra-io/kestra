<template>
    <EmptyTemplate class="demo-layout">
        <div class="content">
            <div class="artwork" aria-hidden="true">
                <img :src="artwork" :alt="image?.alt ?? ''">
            </div>
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
    import artwork from "../../assets/empty_visuals/assets-illus.svg"

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
        max-width: 370px;
    }

    .artwork {
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

    .title {
        margin: 0;
        font-size: 18px;
        font-weight: var(--ks-font-weight-semibold);
        line-height: 22px;
        color: var(--ks-content-primary);
    }

    .message {
        margin: 0;
        font-size: var(--ks-font-size-sm);
        font-weight: var(--ks-font-weight-regular);
        line-height: 18px;
        color: var(--ks-text-secondary);
    }

    .actions {
        display: flex;
        gap: 0.5rem;
        margin-top: 12px;
        justify-content: flex-start;

        :deep(.kel-button + .kel-button) {
            margin-left: 0;
        }
    }

    .learn-more {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        margin-top: 4px;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
        text-decoration: none;

        &:hover {
            color: var(--ks-content-link);
        }
    }
</style>
