<template>
    <topology
        v-if="flow && flowGraph"
        :flow-id="flow.id"
        :namespace="flow.namespace"
        :flow-graph="flowGraph"
    />
</template>
<script>
    import {mapState} from "vuex";
    import Topology from "../graph/Topology.vue";

    export default {
        components: {
            Topology,
        },
        props: {
            preventRouteInfo: {
                type: Boolean,
                default: false
            }
        },
        computed: {
            ...mapState("flow", ["flow", "flowGraph"]),
        },
    };
</script>
