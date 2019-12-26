<template>
    <b-row>
        <b-col>
            <topology-tree ref="topology" v-if="tree" :tree="tree" :label="getLabel" :fill="fill" />
            <!-- <pre>{{execution}}</pre> -->
        </b-col>
    </b-row>
</template>
<script>
import TopologyTree from "../TopologyTree";
import { mapState } from "vuex";
export default {
    components: {
        TopologyTree
    },
    computed: {
        ...mapState("execution", ["execution"]),
        tree() {
            if (this.execution) {
                return {
                    id: "root",
                    tasks:
                        JSON.parse(JSON.stringify(this.execution))
                            .taskRunList || []
                };
            }
            return undefined;
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
        },
        fill(node) {
            if (node.data.state) {
                return {
                    SUCCESS: "#c9fc8d",
                    FAILED: "red",
                    RUNNING: "orange"
                }[node.data.state.current];
            }
            return "#c9fc8d";
        }
    }
};
</script>