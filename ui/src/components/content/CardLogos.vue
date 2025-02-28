<template>
    <div class="row card-group mb-4 pb-2">
        <div class="logo-item col-12 col-sm-4">
            <img
                class="zoom"
                width="222.67px"
                height="125px"
                loading="lazy"
                :src="$store.getters['doc/resourceUrl']('/docs/tutorial/logos/logo-dark-version.png')"
                alt="Dark version logo"
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
                :src="$store.getters['doc/resourceUrl']('/docs/tutorial/logos/logo-light-version.png')"
                alt="Light version logo"
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
                :src="$store.getters['doc/resourceUrl']('/docs/tutorial/logos/logo-monogram-version.png')"
                alt="Monogram version logo"
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

<style lang="scss" scoped>
    @import "@kestra-io/ui-libs/src/scss/variables";

    .logo-item {
        display: flex;
        flex-direction: column;
        gap: calc($spacer * 0.5);

        p {
            margin: 0;
        }

        p.title {
            font-size: $h6-font-size;
            font-weight: 600;
            line-height: 28px;
        }

        p.description {
            color: $white-3;
            font-size: $font-size-base;
            font-weight: 400;
            line-height: 20px;
            text-align: left;
        }
    }


</style>