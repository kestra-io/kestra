<template>
    <FlowConcurrency v-if="execution?.state?.current === 'QUEUED' && flowStore.flow" />

    <div v-else v-ks-loading="true" style="height: 100%; position: relative" />
</template>

<script setup lang="ts">
    import {PropType, onMounted} from "vue"
    import FlowConcurrency from "../flows/FlowConcurrency.vue"
    import {useFlowStore} from "../../stores/flow"

    interface ExecutionState {
        current: string;
    }

    interface Execution {
        namespace: string;
        flowId: string;
        state: ExecutionState;
    }

    const props = defineProps({
        execution: {
            type: Object as PropType<Execution>,
            required: true,
        },
    })

    const flowStore = useFlowStore()
    onMounted(async () => {
        if (props.execution?.state?.current === "QUEUED") {
            if (!flowStore.flow || flowStore.flow.id !== props.execution.flowId) {
                await flowStore.loadFlow({
                    namespace: props.execution.namespace,
                    id: props.execution.flowId,
                })
            }
        }
    })
</script>
