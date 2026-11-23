<template>
    <Executions
        :namespace="flowStore.flow?.namespace"
        :flowId="flowStore.flow?.id"
        :topbar="false"
        fitHeight
        :defaultScopeFilter="false"
        :embed="embed"
        filter
    />
</template>

<script setup lang="ts">
    import Executions from "../executions/Executions.vue"

    import {useFlowStore} from "../../stores/flow"
    const flowStore = useFlowStore()

    defineProps<{
        embed?: boolean;
    }>()

    defineOptions({inheritAttrs: false})
</script>
