<template>
    <nav class="header">
        <h4 class="text-uppercase welcome">
            {{ $t("blueprints.header.welcome") }}
        </h4>
        <h4 class="catch-phrase">
            {{ $t("blueprints.header.catch phrase.1") }}
        </h4>
        <h4 class="catch-phrase">
            {{ $t("blueprints.header.catch phrase.2") }}
        </h4>
    </nav>
</template>

<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables.scss";

    .header {
        background: linear-gradient(140deg, #9535D0 3.03%, #6A22BB 5.45%, #461A97 11.72%, #36188D 21.59%, #321974 35.13%, #25185C 46.92%, #24155B 63.48%, #25155B 75.13%, #450F95 90.68%, #4F39B3 94.9%, #893FE5 97.9%);
        text-align: center;
        padding-top: calc(7 * var(--spacer));
        padding-bottom: calc(3 * var(--spacer));

        .welcome {
            color: $pink;
            font-family: $font-family-monospace;
            font-weight: bold;
        }

        .catch-phrase {
            color: $white;
        }
    }
</style>