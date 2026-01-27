<template>
    <EmptyTemplate class="queued">
        <img src="../../assets/queued_visual.svg" alt="Queued Execution">
        <h5 class="mt-4 fw-bold">
            {{ $t('execution_status') }} 
            <span 
                class="ms-2 px-2 py-1 rounded fs-7 fw-normal" 
                :style="getStyle(execution.state.current)"
            >
                {{ execution.state.current }}
            </span>
        </h5>

        <div v-if="execution.state.current === 'QUEUED'" class="mt-4 w-100">
            <template v-if="flowStore.flow">
                <p class="mb-3 text-start fw-bold text-uppercase fs-7 opacity-75">
                    {{ $t('concurrency_reason') }}
                </p>
                <FlowConcurrency />
            </template>
            <div v-else class="mt-4">
                <el-skeleton :rows="3" animated />
            </div>
        </div>

        <template v-else>
            <p class="mt-4 mb-0">
                {{ $t('no_tasks_running') }}
            </p>
            <p>
                {{ $t('execution_starts_progress') }}
            </p>
        </template>
    </EmptyTemplate>
</template>

<script setup lang="ts">
    import {PropType, onMounted} from "vue";
    import EmptyTemplate from "../layout/EmptyTemplate.vue";
    import FlowConcurrency from "../flows/FlowConcurrency.vue";
    import {useFlowStore} from "../../stores/flow";

    // Kestra standard interfaces for strict typing
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
            required: true
        }
    });

    const flowStore = useFlowStore();

    // Logic to ensure the Concurrency block isn't empty
    onMounted(async () => {
        if (props.execution && props.execution.state.current === "QUEUED") {
            // Only fetch if the store doesn't already have the correct flow
            if (!flowStore.flow || flowStore.flow.id !== props.execution.flowId) {
                await flowStore.loadFlow({
                    namespace: props.execution.namespace, 
                    id: props.execution.flowId
                });
            }
        }
    });

    const getStyle = (state: string) => ({
        color: `var(--ks-content-${state.toLowerCase()})`,
        border: `1px solid var(--ks-border-${state.toLowerCase()})`,
        backgroundColor: `var(--ks-background-${state.toLowerCase()})`
    })
</script>

<style scoped lang="scss">
.queued {
    margin-top: -2rem;
    /* Ensure the concurrency table doesn't overflow the template center */
    :deep(.el-card) {
        text-align: left;
    }
}

p {
    color: var(--ks-content-secondary);
}

.fs-7 {
    font-size: 0.85rem;
}
</style>