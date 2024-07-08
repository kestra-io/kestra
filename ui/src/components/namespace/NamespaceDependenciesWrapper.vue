<template>
    <NamespaceDependencies :id="$route.query?.id ?? $route.params.id" />
</template>

<script setup>
    import NamespaceDependencies from "./NamespaceDependencies.vue";
</script>