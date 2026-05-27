<template>
    <div class="settings-wrapper">
        <slot />
    </div>
</template>

<style scoped lang="scss">
    .settings-wrapper {
        max-width: 42rem;
        margin: 0 auto;
        padding: 2rem 1rem 4rem;
        display: flex;
        flex-direction: column;
        gap: 2.5rem;
    }
</style>
