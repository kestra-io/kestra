<template>
    <div>
        <b-row class="topology-wrapper">
            <b-col>
                <topology-tree
                    :is-flow="true"
                    v-if="flow && flowGraph"
                    :flow-id="flow.id"
                    :namespace="flow.namespace"
                    :flow-graph="flowGraph"
                    :label="getLabel"
                />
            </b-col>
        </b-row>
        <b-row>
            <b-col>
                <task-details />
            </b-col>
        </b-row>
    </div>
</template>
<script>
    import {mapState} from "vuex";
    import TopologyTree from "../graph/TopologyTree";
    import TaskDetails from "./TaskDetails";

    export default {
        components: {
            TopologyTree,
            TaskDetails
        },
        computed: {
            ...mapState("flow", ["flow", "flowGraph"]),
        },
        methods: {
            getLabel (node) {
                const id = node.data.id;
                return `${id.substr(0, 25)}${id.length > 25 ? "..." : ""}`;
            }
        }
    };
</script>
<style lang="scss" scoped>
.topology-wrapper {
    border: 1px solid #bbb;
    padding: 0;
    margin: 0;
    .col {
        padding: 0;
        margin: 0;
    }
}
</style>