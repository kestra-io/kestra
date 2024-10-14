<template>
    <Flows :namespace="$route.params.id || $route.query.id" />
</template>

<script>
    import Flows from "../flows/Flows.vue";

    export default {
        components: {Flows},
    };
</script>