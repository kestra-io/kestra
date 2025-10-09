<template>
    <MultiPanelFlowEditorView
        v-if="flowStore.flow"
    />
</template>

<script setup>
    import {onBeforeUnmount} from "vue"
    import {useFlowStore} from "../../stores/flow";
    import MultiPanelFlowEditorView from "./MultiPanelFlowEditorView.vue";

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
