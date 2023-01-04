<template>
    <topology
        v-if="execution && flowGraph"
        :flow-id="execution.flowId"
        :namespace="execution.namespace"
        :flow-graph="flowGraph"
        :execution="execution"
        @follow="forwardEvent('follow', $event)"
    />
</template>
<script>
    import Topology from "../graph/Topology.vue";
    import {mapState} from "vuex";
    export default {
        components: {
            Topology
        },
        computed: {
            ...mapState("flow", ["flowGraph"]),
            ...mapState("execution", ["execution"])
        },
        props: {
            preventRouteInfo: {
                type: Boolean,
                default: false
            }
        },
        data() {
            return {
                previousExecutionId: undefined
            };
        },
        watch: {
            execution: function () {
                this.loadGraph();
            },
        },
        mounted() {
            this.loadGraph();
        },
        methods: {
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            loadGraph() {
                if (this.execution && (this.flowGraph === undefined || this.previousExecutionId !== this.execution.id)) {
                    this.previousExecutionId = this.execution.id;
                    this.$store.dispatch("flow/loadGraph", {
                        namespace: this.execution.namespace,
                        id: this.execution.flowId,
                        revision: this.execution.flowRevision
                    })
                }
            },
        }
    };
</script>
