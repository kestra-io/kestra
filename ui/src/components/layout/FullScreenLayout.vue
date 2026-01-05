<template>
    <div class="fullscreen-layout">
        <router-view />
    </div>
</template>

<script setup lang="ts">
// Full screen layout without sidebar/navigation for setup process
</script>

<style scoped>
.fullscreen-layout {
    min-height: 100vh;
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--ks-background-body);
    padding: 1rem;
    overflow-y: auto;

    @media screen and (max-width: 992px) {
        align-items: stretch;
    }
}
</style>