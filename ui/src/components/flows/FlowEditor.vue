<template>
    <template v-if="multiPanelEditor">
        <multi-panel-editor-view
            v-if="flow"
            :is-read-only="isReadOnly"
            :expanded-subflows="expandedSubflows"
        />
    </template>
    <template v-else>
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
</template>

<script setup>
    import {onBeforeUnmount, computed} from "vue"
    import {useStore} from "vuex";
    import {useLocalStorage} from "@vueuse/core";
    import EditorView from "../inputs/EditorView.vue";
    import MultiPanelEditorView from "./MultiPanelEditorView.vue";

    defineEmits([
        "expand-subflow"
    ])

    defineProps({
        isReadOnly: {
            type: Boolean,
            default: false
        },
        expandedSubflows: {
            type: Array,
            default: () => []
        }
    })

    const store = useStore();
    const multiPanelEditor = useLocalStorage("multiPanelEditor", false);
    const flow = computed(() => store.state.flow.flow);
    const flowGraph = computed(() => store.state.flow.flowGraph);
    const flowValidation = computed(() => store.getters["flow/flowValidation"]);

    onBeforeUnmount(() => {
        store.commit("flow/setFlowValidation", undefined);
    })
</script>
