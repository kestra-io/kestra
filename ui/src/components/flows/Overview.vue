<template>
    <div>
        <b-row class="topology-wrapper">
            <b-col>
                <topology-tree v-if="flow" :tree="flow" @onNodeClick="onNodeClick" :label="getLabel" :fill="fill"/>
            </b-col>
        </b-row>
        <b-row>
            <b-col>
                <task-details :task="node" />
            </b-col>
        </b-row>
    </div>
</template>
<script>
import { mapState } from "vuex";
import TopologyTree from "../TopologyTree";
import TaskDetails from "./TaskDetails";

export default {
    components: {
        TopologyTree,
        TaskDetails
    },
    data() {
        return {
            node: undefined
        };
    },
    computed: {
        ...mapState("flow", ["flow"])
    },
    methods: {
        onNodeClick(node) {
            this.node = node;
        },
        getLabel (node) {
            const id = node.data.id;
            return `${id.substr(0, 25)}${id.length > 25 ? "..." : ""}`;
        },
        fill () {
            return "#c9fc8d"
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