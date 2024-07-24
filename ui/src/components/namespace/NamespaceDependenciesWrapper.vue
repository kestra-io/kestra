<template>
    <NamespaceDependencies :namespace="namespace" />
</template>

<script setup>
    import {computed} from "vue";

    import {useRoute} from "vue-router";
    const route = useRoute();

    const namespace = computed(() => route.params.id);

    import NamespaceDependencies from "./NamespaceDependencies.vue";
</script>