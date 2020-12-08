<template>
    <b-row>
        <b-col>
            <topology-tree
                ref="topology"
                v-if="execution && flowGraph"
                :flow-graph="flowGraph"
                :flow-id="execution.flowId"
                :namespace="execution.namespace"
                :is-flow="false"
                :label="getLabel"
            />
        </b-col>
    </b-row>
</template>
<script>
    import TopologyTree from "../graph/TopologyTree";
    import {mapState} from "vuex";
    export default {
        components: {
            TopologyTree
        },
        computed: {
            ...mapState("execution", ["execution", "flowGraph"])
        },
        created() {
            if (!this.flowGraph && this.execution) {
                this.$store.dispatch("execution/loadGraph", this.execution)
            }
        },
        methods: {
            getLabel(node) {
                return node.data.taskId;
            },
            update() {
                if (this.$refs.topology) {
                    this.$refs.topology.update();
                }
            }
        }
    };
</script>
