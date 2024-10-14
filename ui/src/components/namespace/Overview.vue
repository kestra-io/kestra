<template>
    <Dashboard :restore-u-r-l="false" :namespace="$route.params.id || $route.query.id" embed />
</template>

<script>
    import Dashboard from "../dashboard/Dashboard.vue";
    import {mapState} from "vuex";

    export default {
        components: {Dashboard},
        computed: {
            ...mapState("namespace", ["namespace"]),
        },
    };
</script>
