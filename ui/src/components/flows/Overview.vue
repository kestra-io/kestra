<template>
    <div>
        <b-row>
            <b-col>
                <topology-tree v-if="flow" :tree="flow" @onNodeClick="onNodeClick" />
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
        }
    }
};
</script>