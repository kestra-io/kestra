<template>
    <multi-panel-editor-view
        v-if="flowStore.flow"
    />
</template>

<script setup>
    import {onBeforeUnmount} from "vue"
    import MultiPanelEditorView from "./MultiPanelEditorView.vue";
    import {useFlowStore} from "../../stores/flow";

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
        },
        embed: {
            type: Boolean,
            default: false
        },
        beta: {
            type: Boolean,
            default: false
        }
    })

    const flowStore = useFlowStore();

    onBeforeUnmount(() => {
        flowStore.flowValidation = undefined;
    })
</script>
