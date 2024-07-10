<template>
    <NamespaceDependencies :id="route.params.namespace" />
</template>

<script setup>
    import {useRoute} from "vue-router";
    const route = useRoute();

    import NamespaceDependencies from "./NamespaceDependencies.vue";
</script>