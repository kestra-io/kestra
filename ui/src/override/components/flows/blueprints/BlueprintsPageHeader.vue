<template>
    <nav class="header container">
        <div class="image-box">
            <img src="../../../../assets/icons/blueprint.svg" :alt="$t('blueprints.header.alt')">
            <img src="../../../../assets/icons/blueprint-dark.svg" :alt="$t('blueprints.header.alt')" class="blueprint-dark">
        </div>
        <h4 class="catch-phrase">
            {{ $t("blueprints.header.catch phrase.2") }}
        </h4>
    </nav>
</template>

<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables.scss";

    .header {
        display: flex;
        align-items: center;
        gap: 16px;
        padding-top: calc(4 * var(--spacer));
        padding-bottom: calc(2 * var(--spacer));

        .catch-phrase {
            color: var(--bs-heading-color);
            margin-bottom: 0;
        }

        .image-box{
            border: 1px solid var(--bs-border-color);
            background-color: var(--bs-card-bg);
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
