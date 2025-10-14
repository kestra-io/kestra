<template>
    <span>{{ totalPlugins }}</span>
</template>

<script setup lang="ts">
    // eslint-disable-next-line no-undef
    const {totalPlugins} = usePluginsCount()
</script>
