<template>
    <div class="fullscreen-layout">
        <router-view />
    </div>
</template>

<script setup>
// Full screen layout without sidebar/navigation for setup process
</script>

<style scoped>
.fullscreen-layout {
    width: 100vw;
    height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--ks-background-body);
}
</style>