<template>
    <EditorSidebar :currentNS="namespace" style="width: 100%;height: 100%;" />
</template>

<script lang="ts" setup>
    import {computed} from "vue";
    // @ts-expect-error no types on editor sidebar
    import EditorSidebar from "./EditorSidebar.vue";
    import {useFlowStore} from "../../stores/flow";

    const flowStore = useFlowStore();
    const namespace = computed(() => flowStore.flow?.namespace);
</script>