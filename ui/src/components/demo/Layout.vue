<template>
    <EmptyTemplate class="demo-layout">
        <img :src="image.source" :alt="image.alt" class="img">
        <div class="message-block">
            <div class="enterprise-tag">
                <div class="flare" />
                {{ $t('demos.enterprise_edition') }}
            </div>
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

    @keyframes move-border {
        0%{background-position: 0% 0%}
        50%{background-position: 100% 100%}
        100%{background-position: 0% 0%}
    }

    .message-block {
        width: 100%;
        max-width: 665px;
        margin: 0 auto;
        padding: 0 1.5rem;

        .enterprise-tag::before,
        .enterprise-tag::after{
            content: "";
            display: block;
            position: absolute;
            border-radius: 1rem;
        }

        .enterprise-tag::before{
            z-index: -2;
            background-image: linear-gradient(138.8deg, #CCE8FE 0%, #CDA0FF 27.03%, #8489F5 41.02%, #CDF1FF 68.68%, #B591E9 94%, #CCE8FE 100%);
            background-size: 200% 200%;
            top: 0px;
            bottom: 0px;
            left: 0px;
            right: 0px;
            animation: move-border 3s linear infinite;
        }

        .enterprise-tag::after{
            z-index: -1;
            background: $base-gray-100;
            top: 1px;
            left: 1px;
            bottom: 1px;
            right: 1px;
            html.dark & {
                background: $base-gray-400;
            }
        }

        .enterprise-tag{
            position: relative;
            background: $base-gray-200;
            padding: .125rem 0.5rem;
            border-radius: 1rem;
            display: inline-block;
            z-index: 2;
            margin: 0 auto;
            font-size: 0.75rem;
            html.dark &{
                background: #FBFBFB26;
            }
            .flare{
                display: none;
                position: absolute;
                content: "";
                height: 2rem;
                width: 2rem;
                z-index: 12;
                top: -1.1rem;
                right: 0;
                background-image:
                    // vertical flare
                    linear-gradient(0deg, rgba($base-gray-200, 0) 0%, $base-gray-200 50%, rgba($base-gray-200, 0) 100%),
                    // horizontal flare
                    linear-gradient(90deg, rgba($base-gray-200, 0) 0%, $base-gray-200 50%, rgba($base-gray-200, 0) 100%),
                    // flare effect
                    radial-gradient(circle, $base-gray-200 0%, rgba($base-gray-200, .1) 50%,rgba($base-gray-200, 0) 70%);
                background-size:  1px 100%, 100% 1px, 40% 40%;
                background-repeat: no-repeat;
                background-position: center, center, center;
                transform:rotate(-13deg);
                &::before{
                    content: "";
                    display: block;
                    position: absolute;
                    height: 2rem;
                    width: 2rem;
                    background-image:
                        // vertical flare
                        linear-gradient(0deg, rgba($base-gray-200, 0) 0%, rgba($base-gray-200, .7) 50%, rgba($base-gray-200, 0) 100%),
                        // horizontal flare
                        linear-gradient(90deg, rgba($base-gray-200, 0) 0%, rgba($base-gray-200, .7) 50%, rgba($base-gray-200, 0) 100%);
                    background-size:  1px 50%, 50% 1px;
                    background-repeat: no-repeat;
                    background-position: center, center, center;
                    transform: rotate(45deg);
                }
                html.dark &{
                    display: block;
                }
            }
        }
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
