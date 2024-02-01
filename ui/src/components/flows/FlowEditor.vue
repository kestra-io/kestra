<template>
    <editor-view
        v-if="flow"
        :flow-id="flow.id"
        :namespace="flow.namespace"
        :flow-graph="flowGraph"
        :flow="flow"
        :is-read-only="isReadOnly"
        :flow-error="flowError"
        :flow-deprecations="flowDeprecations"
        :expanded-subflows="expandedSubflows"
        @expand-subflow="$emit('expand-subflow', $event)"
    />
</template>
<script>
    import {mapGetters, mapState} from "vuex";
    import EditorView from "../inputs/EditorView.vue";

    export default {
        components: {
            EditorView,
        },
        emits: [
            "expand-subflow"
        ],
        props: {
            isReadOnly: {
                type: Boolean,
                default: false
            },
            expandedSubflows: {
                type: Array,
                default: () => []
            }
        },
        computed: {
            ...mapState("flow", ["flow", "flowGraph"]),
            ...mapGetters("flow", ["flowError", "flowDeprecations"]),
        },
        beforeUnmount() {
            this.$store.commit("flow/setFlowError", undefined);
            this.$store.commit("flow/setFlowDeprecations", undefined);
        },
    };
</script>
