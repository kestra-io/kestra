<template>
    <section class="container empty-template">
        <slot />
    </section>
</template>

<style lang="scss" scoped>
    .empty-template {
            margin-top: 0;
            padding: 3rem 0;
            text-align: center;
            background-repeat: no-repeat;
            background-image: url("../../assets/empty-page-light.svg#file");
            background-position: top center;
            html.dark & {
                background-image: url("../../assets/empty-page-dark.svg#file");
                background-position: top center;
            }
    }
</style>