<template>
    <topology-tree
        ref="topology"
        v-if="execution && flowGraph"
        :flow-graph="flowGraph"
        :flow-id="execution.flowId"
        :namespace="execution.namespace"
        :execution="execution"
    />
</template>
<script>
    import TopologyTree from "../graph/TopologyTree";
    import {mapState} from "vuex";
    export default {
        components: {
            TopologyTree
        },
        computed: {
            ...mapState("flow", ["flowGraph"]),
            ...mapState("execution", ["execution"])
        },
        watch: {
            execution: function () {
                if (this.flowGraph === undefined && this.execution) {
                    this.loadGraph();
                }
            },
        },
        mounted() {
            if (this.execution) {
                this.loadGraph();
            }
        },
        methods: {
            loadGraph() {
                this.$store.dispatch("flow/loadGraph", {
                    namespace: this.execution.namespace,
                    id: this.execution.flowId,
                    revision: this.execution.flowRevision
                })
            },
        }
    };
</script>
