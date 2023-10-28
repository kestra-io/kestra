<template>
    <nav class="header">
        <h4 class="catch-phrase fw-bold">
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
        background: url("../../../../assets/dot-bg.jpg") no-repeat;
        text-align: center;
        padding-top: calc(4 * var(--spacer));
        padding-bottom: calc(6 * var(--spacer));

        .catch-phrase {
            color: $white;
        }
    }
</style>