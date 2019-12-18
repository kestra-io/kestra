<template>
    <b-row>
        <b-col>
            <topology-tree v-if="flow" :tree="flow"/>
        </b-col>
    </b-row>
</template>
<script>
import { mapState } from "vuex";
import TopologyTree from "../TopologyTree";

export default {
    components: {
        TopologyTree
    },
    created() {
        this.$store
            .dispatch("flow/loadFlow", this.$route.params)
            .then(this.onFlowReady);
    },
    computed: {
        ...mapState("flow", ["flow"])
    }
};
</script>