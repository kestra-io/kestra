<template>
    <div :class="['fullscreen-layout',{'always-center':isAlwaysCenter}]">
        <router-view />
    </div>
</template>

<script setup>
// Full screen layout without sidebar/navigation for setup process
    import {computed} from "vue"
    import {useRoute} from "vue-router"

    const route = useRoute()

    // read meta flag; fallback false
    const isAlwaysCenter = computed(() => !!route.meta.alwaysCenter)

</script>

<style scoped>

.fullscreen-layout {
    width: 100vw;
    height: 100vh;
    display: flex;
    justify-content: center;
    background: var(--ks-background-body);
    overflow-y: scroll;
}
@media (min-width:992px) {
    .fullscreen-layout{
    align-items: center;
    }
}
.fullscreen-layout.always-center {
    align-items: center;
}
</style>