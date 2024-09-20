<template>
    <Dashboard :flow="flow.id" embed />
</template>

<script>
    import Dashboard from "../dashboard/Dashboard.vue";
    import {mapGetters} from "vuex";

    export default {
        components: {Dashboard},
        emits: ["expandSubflow"],
        computed: {
            ...mapGetters("flow", ["flow"]),
        },
    };
</script>
