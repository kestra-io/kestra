<template>
    <Executions 
        :restoreUrl="false" 
        filter 
        :topbar="false" 
        :namespace="flowStore.flow?.namespace" 
        :flowId="flowStore.flow?.id" 
    />
</template>

<script setup lang="ts">
    import Executions from "../executions/Executions.vue";
    import {useFlowStore} from "../../stores/flow";

    // This will get the flowstore instance
    const flowStore = useFlowStore();

    // inheritAttrs
    defineOptions({
        inheritAttrs: false
    });
</script>