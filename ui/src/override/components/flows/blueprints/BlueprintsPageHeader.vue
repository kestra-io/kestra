<template>
    <nav class="header">
        <div class="image-box">
            <img src="../../../../assets/icons/blueprint.svg" :alt="$t('blueprints.header.alt')">
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
        padding: calc(5 * var(--spacer));
        padding-top: calc(3 * var(--spacer));
        padding-bottom: calc(2 * var(--spacer));

        .catch-phrase {
            color: $white;
            margin-bottom: 0;
        }

        .image-box{
            border: 1px solid var(--bs-border-color);
            background-color: #2F334280;
            padding: 9px;
            border-radius: 7px;
            box-shadow:
                0px 4px 12px 0px #53009F,
                1px 1px 0px 0px #FF4BBD;
        }
    }
</style>