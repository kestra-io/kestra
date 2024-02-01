<template>
    <executions :restore-url="false" filter :topbar="false" :namespace="flow.namespace" :flow-id="flow.id" />
</template>

<script>
    import Executions from "../executions/Executions.vue";
    import {mapState} from "vuex";

    export default {
        inheritAttrs: false,
        components: {
            Executions,
        },
        computed: {
            ...mapState("flow", ["flow"]),
        }
    };
</script>
