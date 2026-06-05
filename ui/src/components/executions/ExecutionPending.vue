<template>
    <FlowConcurrency v-if="execution?.state?.current === 'QUEUED' && flowStore.flow" />
    <EmptyTemplate v-else class="queued">
        <img src="../../assets/queued_visual.svg" alt="Queued Execution">
        <h5 class="mt-4 fw-bold pending-status">
            {{ $t('execution_status') }}
            <KsExecutionStatus v-if="execution?.state?.current" :status="execution.state.current" />
        </h5>
        <p class="mt-4 mb-0">
            {{ $t('no_tasks_running') }}
        </p>
        <p>
            {{ $t('execution_starts_progress') }}
        </p>
    </EmptyTemplate>
</template>

<script setup lang="ts">
    import {PropType, onMounted} from "vue"
    import {KsExecutionStatus} from "@kestra-io/design-system"
    import EmptyTemplate from "../layout/EmptyTemplate.vue"
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

<style scoped lang="scss">
.queued {
    margin-top: -2rem;
}

.pending-status {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: var(--ks-spacing-2);
}

p {
    color: var(--ks-text-secondary);
}
</style>
