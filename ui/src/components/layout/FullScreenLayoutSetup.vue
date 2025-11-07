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
    justify-content: center;
    background: var(--ks-background-body);
    overflow-y: scroll;
    
    @media (min-width:992px) {
        align-items: center;
    }
}
</style>