<template>
    <div>
        <b-row class="topology-wrapper">
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