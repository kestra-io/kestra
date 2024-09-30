<template>
    <div class="row card-group mb-4 pb-2">
        <div class="logo-item col-12 col-sm-4">
            <img
                class="zoom"
                width="222.67px"
                height="125px"
                loading="lazy"
                :src="$store.getters['doc/resourceUrl']('/v1/docs/tutorial/logos/logo-dark-version.png')"
                alt="Dark version logo"
            >
            <p class="title">
                Dark version
            </p>
            <p class="description">
                Use this version when working on a dark background to ensure our name remains readable.
            </p>
        </div>

        <div class="logo-item col-12 col-sm-4">
            <img
                class="zoom"
                width="222.67px"
                height="125px"
                loading="lazy"
                :src="$store.getters['doc/resourceUrl']('/v1/docs/tutorial/logos/logo-light-version.png')"
                alt="Light version logo"
            >
            <p class="title">
                Light version
            </p>
            <p class="description">
                This is the preferred option when working with a light background.
            </p>
        </div>

        <div class="logo-item col-12 col-sm-4">
            <img
                class="zoom"
                width="222.67px"
                height="125px"
                loading="lazy"
                :src="$store.getters['doc/resourceUrl']('/v1/docs/tutorial/logos/logo-monogram-version.png')"
                alt="Monogram version logo"
            >
            <p class="title">
                Monogram
            </p>
            <p class="description">
                The wordmark colors adapt based on the background, while the icon remains without a background when placed on a dark background.
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