<template>
    <Flows :restore-url="false" :topbar="false" :namespace="$route.params.id || $route.query.id" />
</template>

<script>
    import Flows from "../flows/Flows.vue";

    export default {
        components: {Flows},
    };
</script>