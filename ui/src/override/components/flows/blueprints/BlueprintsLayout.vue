<template>
    <div class="blueprints">
        <slot />
    </div>
</template>

<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables.scss";

    .blueprints {
        background: var(--bg-dots-image) no-repeat top left;
        background-color: var(--bg-dots-color);
        background-size: 1654px;
    }
</style>
