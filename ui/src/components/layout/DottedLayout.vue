<template>
    <slot v-if="embed" />
    <div class="blueprints" v-else>
        <nav class="header">
            <div class="image-box">
                <img :src="image" :alt="alt || phrase">
                <img :src="imageDark" :alt="alt || phrase" class="blueprint-dark">
            </div>
            <h4 class="catch-phrase">
                {{ phrase }}
            </h4>
        </nav>
        <slot />
    </div>
</template>

<script setup lang="ts">
    defineProps<{
        embed: boolean;
        phrase: string;
        alt?: string;
        image: string;
        imageDark: string;
    }>();
</script>

<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    .blueprints {
        background: url('../../assets/dots-bg.jpg') no-repeat top left;
        background-color: #FAFAFF;
        flex: 1;
        .dark & {
            background:  url('../../assets/dots-bg-dark.jpg') no-repeat top left;
            background-color: #1B1E27;
        }
    }

    .header {
        display: flex;
        align-items: center;
        gap: 16px;
        padding-top: 4rem;
        padding-bottom: 1rem;
        margin: 0 2rem;

        .catch-phrase {
            color: var(--ks-content-primary);
            margin-bottom: 0;
        }

        .image-box{
            border: 1px solid var(--ks-border-primary);
            background-color: var(--ks-background-card);
            padding: 9px;
            border-radius: 7px;
            box-shadow:
                0px 4px 12px 0px #53009F0D,
                1px 1px 0px 0px #FF4BBD,
                1px 1px 0px 0px #FFFFFF0D inset;

            .dark & {
                box-shadow:
                    0px 4px 12px 0px #53009F,
                    1px 1px 0px 0px #FF4BBD
                    1px 1px 0px 0px #FFFFFF0D inset;
            }

            & > img.blueprint-dark {
                display: none;
            }
        }

        .dark & {
            .image-box > img{
                display: none;
            }

            .image-box > img.blueprint-dark{
                display: block;
            }
        }
    }
</style>
