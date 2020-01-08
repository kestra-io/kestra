<template>
    <b-row>
        <b-col>
            <topology-tree
                ref="topology"
                v-if="execution && dataTree"
                :dataTree="dataTree"
                :label="getLabel"
            />
        </b-col>
    </b-row>
</template>
<script>
import TopologyTree from "../graph/TopologyTree";
import { mapState } from "vuex";
export default {
    components: {
        TopologyTree
    },
    computed: {
        ...mapState("execution", ["execution", "dataTree"])
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