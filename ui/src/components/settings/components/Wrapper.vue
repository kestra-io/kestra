<template>
    <div class="settings-wrapper">
        <slot />
    </div>
</template>

<style scoped lang="scss">
    .settings-wrapper {
        width: min(600px, 100% - var(--ks-spacing-6));
        margin: 0 auto;
        padding-block: var(--ks-spacing-6) var(--ks-spacing-10);
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-10);
    }
</style>
