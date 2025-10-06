<template>
    <Executions :restoreUrl="false" filter :topbar="false" :namespace="flowStore.flow?.namespace" :flowId="flowStore.flow?.id" />
</template>

<script>
    import {mapStores} from "pinia";
    import Executions from "../executions/Executions.vue";
    import {useFlowStore} from "../../stores/flow";

    export default {
        inheritAttrs: false,
        components: {
            Executions,
        },
        computed: {
            ...mapStores(useFlowStore)
        }
    };
</script>
