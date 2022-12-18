<template>
    <topology-tree
        v-if="flow && flowGraph"
        :flow-id="flow.id"
        :namespace="flow.namespace"
        :flow-graph="flowGraph"
    />
</template>
<script>
    import {mapState} from "vuex";
    import TopologyTree from "../graph/TopologyTree.vue";

    export default {
        components: {
            TopologyTree,
        },
        computed: {
            ...mapState("flow", ["flow", "flowGraph"]),
        },
    };
</script>
