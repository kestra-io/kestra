<template>
    <NamespaceDependencies :namespace="$route.params.id" />
</template>

<script setup>
    import NamespaceDependencies from "./NamespaceDependencies.vue";
</script>