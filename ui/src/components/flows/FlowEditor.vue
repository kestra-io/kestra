<template>
    <editor-view
        v-if="flow"
        :flow-id="flow.id"
        :namespace="flow.namespace"
        :flow-graph="flowGraph"
        :flow="flow"
        :is-read-only="isReadOnly"
        :flow-validation="flowValidation"
        :expanded-subflows="expandedSubflows"
        @expand-subflow="$emit('expand-subflow', $event)"
        :next-revision="flow.revision + 1"
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
            ...mapGetters("flow", ["flowValidation"]),
        },
        beforeUnmount() {
            this.$store.commit("flow/setFlowValidation", undefined);
        },
    };
</script>
