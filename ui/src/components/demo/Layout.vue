<template>
    <EmptyTemplate class="demo-layout">
        <img :src="image.source" :alt="image.alt" class="img">
        <div class="message-block">
            <EnterpriseTag>
                {{ $t('demos.enterprise_edition') }}
            </EnterpriseTag>
        </div>
        <div class="msg-block">
            <h2>{{ title }}</h2>
            <div v-if="isOnline && video" class="video-container">
                <iframe
                    v-if="video.source"
                    :src="video.source"
                    allowfullscreen
                    allow="accelerometer; clipboard-write; encrypted-media; gyroscope;"
                />
            </div>
            <p><slot name="message" /></p>
            <DemoButtons />
        </div>
    </EmptyTemplate>
</template>

<script setup lang="ts">
    import {useNetwork} from "@vueuse/core"
    const {isOnline} = useNetwork()
    
    import EmptyTemplate from "../layout/EmptyTemplate.vue";
    import DemoButtons from "./DemoButtons.vue";
    import EnterpriseTag from "../EnterpriseTag.vue";

    defineProps<{
        title: string;
        image: {
            source: string;
            alt: string;
        };
        video?: {
            source: string;
        };
        embed?: boolean;
    }>();
</script>

<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/color-palette.scss";
    @import "@kestra-io/ui-libs/src/scss/_variables.scss";

    .demo-layout {
        padding: $spacer 0 !important;
        margin-top: 0 !important;
    }

    .img {
        width: 253px;
        height: 212px;
        margin-bottom: -1.5rem;
    }

    .message-block {
        width: 100%;
        max-width: 665px;
        margin: 0 auto;
        padding: 0 1.5rem;
    }

    .msg-block {
        text-align: left;
        width: 100%;
        max-width: 665px;
        margin: 0 auto;
        padding: 0 1.5rem;

        h2 {
            margin: 1rem 0;
            line-height: 20px;
            font-size: 14px;
            font-weight: 600;
            text-align: center;
        }

        p {
            line-height: 16px;
            font-size: 11px;
            text-align: left;
            color: var(--ks-content-secondary);
        }

        .video-container {
            position: relative;
            padding-bottom: 56.25%;
            border-radius: $border-radius;
            border: 1px solid var(--ks-border-primary);
            overflow: hidden;
            margin: $spacer auto;

            iframe {
                position: absolute;
                top: 50%;
                left: 50%;
                transform: translate(-50%, -50%);
                width: 100%;
                height: 100%;
                object-fit: contain;
                margin: 0;
            }
        }
    }

    .img {
        width: 60%;
        height: auto;
        margin-bottom: -1.5rem;
    }

    @include media-breakpoint-up(md) {
        .message-block,
        .msg-block {
            padding: 0 1rem;
        }

        .enterprise-tag {
            padding: .125rem 0.75rem;
            font-size: 0.8125rem;
        }

        .msg-block {
            h2 {
                font-size: 16px;
                line-height: 24px;
            }

            p {
                font-size: 12px;
                line-height: 18px;
            }
        }
    }

    @include media-breakpoint-up(lg) {
        .enterprise-tag {
            font-size: 0.875rem;
            padding: .125rem 1rem;
        }

        .msg-block {
            h2 {
                font-size: 18px;
                line-height: 26px;
                margin: 1.5rem 0;
            }

            p {
                font-size: 13px;
                line-height: 20px;
            }
        }

        .img {
            width: 253px;
            height: 212px;
        }
    }

    @include media-breakpoint-up(xl) {
        .msg-block {
            h2 {
                font-size: 20px;
                line-height: 30px;
            }

            p {
                font-size: 1rem;
                line-height: 22px;
            }
        }
    }
</style>
