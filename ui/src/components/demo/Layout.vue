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
            <DemoButtons :type="type" />
        </div>
    </EmptyTemplate>
</template>

<script setup lang="ts">
    import {useNetwork} from "@vueuse/core"
    const {isOnline} = useNetwork()

    import EmptyTemplate from "../layout/EmptyTemplate.vue"
    import DemoButtons from "./DemoButtons.vue"
    import EnterpriseTag from "../EnterpriseTag.vue"

    defineProps<{
        title: string;
        type: string;
        image: {
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
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    .demo-layout {
        padding: 1rem 0 !important;
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
            line-height: var(--ks-font-size-lg);
            font-size: var(--ks-font-size-md);
            font-weight: 600;
            text-align: center;
        }

        p {
            line-height: 16px;
            font-size: var(--ks-font-size-sm);
            text-align: left;
            color: var(--ks-content-secondary);
        }

        .video-container {
            position: relative;
            padding-bottom: 56.25%;
            border-radius: 0.25rem;
            border: 1px solid var(--ks-border-primary);
            overflow: hidden;
            margin: 1rem auto;

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

    @include res(md) {
        .message-block,
        .msg-block {
            padding: 0 1rem;
        }

        .enterprise-tag {
            padding: .125rem 0.75rem;
            font-size: var(--ks-font-size-sm);
        }

        .msg-block {
            h2 {
                font-size: var(--ks-font-size-base);
                line-height: 24px;
            }

            p {
                font-size: var(--ks-font-size-xs);
                line-height: 18px;
            }
        }
    }

    @include res(lg) {
        .enterprise-tag {
            font-size: var(--ks-font-size-sm);
            padding: .125rem 1rem;
        }

        .msg-block {
            h2 {
                font-size: var(--ks-font-size-md);
                line-height: 26px;
                margin: 1.5rem 0;
            }

            p {
                font-size: var(--ks-font-size-xs);
                line-height: var(--ks-font-size-lg);
            }
        }

        .img {
            width: 253px;
            height: 212px;
        }
    }

    @include res(xl) {
        .msg-block {
            h2 {
                font-size: var(--ks-font-size-lg);
                line-height: 30px;
            }

            p {
                font-size: var(--ks-font-size-base);
                line-height: 22px;
            }
        }
    }
</style>
