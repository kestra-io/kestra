<template>
    <EditorSidebar :current-n-s="namespace" style="width: 100%;height: 100%;" />
</template>

<script lang="ts" setup>
    import {computed} from "vue";
    import EditorSidebar from "./EditorSidebar.vue";
    import {useFlowStore} from "../../stores/flow";

    const flowStore = useFlowStore();
    const namespace = computed(() => flowStore.flow?.namespace);
</script>