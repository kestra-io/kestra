<template>
    <Executions
        :namespace="flowStore.flow?.namespace"
        :flowId="flowStore.flow?.id"
        :topbar="false"
        :restoreUrl="false"
        filter
    />
</template>

<script setup lang="ts">
    import Executions from "../executions/Executions.vue";

    import {useFlowStore} from "../../stores/flow";
    const flowStore = useFlowStore();

    defineOptions({inheritAttrs: false});
</script>
