<template>
    <div class="settings-wrapper">
        <slot />
    </div>
</template>

<style scoped lang="scss">
    .settings-wrapper {
        width: min(600px, 100% - 2rem);
        margin: 0 auto;
        padding-block: 2rem 4rem;
        display: flex;
        flex-direction: column;
        gap: 4rem;
    }
</style>
