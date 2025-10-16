<template>
    <EditorSidebar
        :currentNS="namespace"
        style="width: 100%;height: 100%;"
        @file-clicked="actions.fileClicked"
        @file-created="actions.fileCreated"
        @file-deleted="actions.fileDeleted"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import EditorSidebar from "./EditorSidebar.vue";
    import {useFlowStore} from "../../stores/flow";
    import {useFileExplorer} from "../../composables/useFileExplorer";

    const flowStore = useFlowStore();
    const namespace = computed(() => flowStore.flow?.namespace);

    const actions = useFileExplorer();
</script>