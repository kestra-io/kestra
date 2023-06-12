<template>
    <editor-view
        v-if="flow"
        :flow-id="flow.id"
        :namespace="flow.namespace"
        :flow-graph="flowGraph"
        :is-read-only="isReadOnly"
        :flow-error="flowError"
    />
</template>
<script>
    import {mapGetters, mapState} from "vuex";
    import EditorView from "../inputs/EditorView.vue";

    export default {
        components: {
            EditorView,
        },
        props: {
            preventRouteInfo: {
                type: Boolean,
                default: false
            },
            isReadOnly: {
                type: Boolean,
                default: false
            }
        },
        computed: {
            ...mapState("flow", ["flow", "flowGraph"]),
            ...mapGetters("flow", ["flowError"]),
        },
        beforeUnmount() {
            this.$store.commit("flow/setFlowError", undefined);
        },
    };
</script>
