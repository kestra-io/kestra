<template>
    <div>
        <Topology
            :flow-id="flowId"
            :namespace="namespace"
            :is-creating="true"
            :flow-graph="flowGraph"
            :is-read-only="false"
            :total="total"
            :guided-properties="guidedProperties"
            :flow-error="flowError"
        />
    </div>
    <div id="guided-right" />
</template>

<script>
    import Topology from "../graph/Topology.vue";
    import {mapGetters, mapState} from "vuex";

    export default {
        components: {
            Topology
        },
        data() {
            return {
                flowId: "",
                namespace: "",
            }
        },
        beforeUnmount() {
            this.$store.commit("flow/setFlowError", undefined);
        },
        computed: {
            ...mapState("flow", ["flowGraph", "total"]),
            ...mapState("auth", ["user"]),
            ...mapState("plugin", ["pluginSingleList", "pluginsDocumentation"]),
            ...mapGetters("core", ["guidedProperties"]),
            ...mapGetters("flow", ["flowError"]),
        },
        beforeRouteLeave(to, from, next) {
            this.$store.commit("flow/setFlow", null);
            next();
        }
    };
</script>
