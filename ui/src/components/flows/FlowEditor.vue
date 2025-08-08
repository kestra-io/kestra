<template>
    <multi-panel-editor-view
        v-if="flow"
    />
</template>

<script setup>
    import {onBeforeUnmount, computed} from "vue"
    import {useStore} from "vuex";
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

    const store = useStore();
    const flow = computed(() => store.state.flow.flow);

    onBeforeUnmount(() => {
        store.commit("flow/setFlowValidation", undefined);
    })
</script>
