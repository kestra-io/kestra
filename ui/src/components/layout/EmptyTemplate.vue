<template>
    <section class="container empty-template">
        <slot />
    </section>
</template>

<style scoped lang="scss">
    .empty-template {
        margin-top: -1.5rem;
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
    @media (max-width: 768px) {
        .empty-template{
            padding: 2rem;
        }
        .container-sm, .container {
            max-width: 768px;
        }
    }
</style>