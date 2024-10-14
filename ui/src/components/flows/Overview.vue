<template>
    <Dashboard :restore-u-r-l="false" flow :flow-i-d="flow ? flow.id : undefined" embed />
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
