<template>
    <div class="row card-group mb-4 pb-2">
        <div class="logo-item col-12 col-sm-4">
            <img
                class="zoom"
                width="222.67px"
                height="125px"
                loading="lazy"
                :src="docStore.resourceUrl('/docs/tutorial/logos/logo-dark-version.png')"
                :alt="$t('dark_version')"
            >
            <p class="title">
                {{ $t('dark_version') }}
            </p>
            <p class="description">
                {{ $t('use_dark_background') }}
            </p>
        </div>

        <div class="logo-item col-12 col-sm-4">
            <img
                class="zoom"
                width="222.67px"
                height="125px"
                loading="lazy"
                :src="docStore.resourceUrl('/docs/tutorial/logos/logo-light-version.png')"
                :alt="$t('light_version')"
            >
            <p class="title">
                {{ $t('light_version') }}
            </p>
            <p class="description">
                {{ $t('light_background') }}
            </p>
        </div>

        <div class="logo-item col-12 col-sm-4">
            <img
                class="zoom"
                width="222.67px"
                height="125px"
                loading="lazy"
                :src="docStore.resourceUrl('/docs/tutorial/logos/logo-monogram-version.png')"
                :alt="$t('monogram')"
            >
            <p class="title">
                {{ $t('monogram') }}
            </p>
            <p class="description">
                {{ $t('wordmark_colors') }}
            </p>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {useDocStore} from "../../stores/doc"

    const docStore = useDocStore()
</script>

<style scoped lang="scss">

    .logo-item {
        display: flex;
        flex-direction: column;
        gap: calc(1rem * 0.5);

        p {
            margin: 0;
        }

        p.title {
            font-size: var(--ks-font-size-base);
            font-weight: 600;
            line-height: 28px;
        }

        p.description {
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-base);
            font-weight: 400;
            line-height: var(--ks-font-size-lg);
            text-align: left;
        }
    }


</style>