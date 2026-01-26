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

        <p class="mt-4 mb-0">
            {{ $t('no_tasks_running') }}
        </p>
        <p>
            {{ $t('execution_starts_progress') }}
        </p>

        <!-- =============================
             Concurrency block (NEW)
        ============================== -->
        <div v-if="runningCountSet" class="mt-4 w-100">
            <el-card class="mb-3">
                <div class="row mb-3">
                    <span class="col d-flex align-items-center">
                        <h5 class="m-0 me-2">RUNNING</h5>
                        {{ runningCount }}/{{ execution?.concurrency?.limit ?? "-" }}
                    </span>

                    <span class="col d-flex justify-content-end align-items-center">
                        {{ $t('behavior') }}:
                        <Status
                            class="mx-2"
                            :status="execution?.concurrency?.behavior"
                            size="small"
                        />
                    </span>
                </div>

                <el-progress
                    :stroke-width="16"
                    color="#5BB8FF"
                    :percentage="progress"
                    :showText="false"
                />
            </el-card>

            <el-card>
                <Executions
                    :key="refreshKey"
                    :restoreUrl="false"
                    :topbar="false"
                    :namespace="execution.namespace"
                    :flowId="execution.flowId"
                    isConcurrency
                    :statuses="[State.QUEUED, State.RUNNING, State.PAUSED]"
                    @state-count="setRunningCount"
                    filter
                />
            </el-card>
        </div>
    </EmptyTemplate>
</template>

<script setup lang="ts">
    import {PropType, ref, computed, onMounted, onUnmounted} from "vue";

    import EmptyTemplate from "../layout/EmptyTemplate.vue";
    import Executions from "./Executions.vue";
    import {State, Status} from "@kestra-io/ui-libs";

    interface ExecutionState {
        current: string;
    }

    interface Execution {
        state: ExecutionState;
        namespace?: string;
        flowId?: string;
        concurrency?: {
            limit?: number;
            behavior?: string;
        };
    }

    const props = defineProps({
        execution: {
            type: Object as PropType<Execution>,
            required: true
        }
    });

    const execution = props.execution;

    /* =============================
   Style helper (existing)
============================= */
    const getStyle = (state: string) => ({
        color: `var(--ks-content-${state.toLowerCase()})`,
        border: `1px solid var(--ks-border-${state.toLowerCase()})`,
        backgroundColor: `var(--ks-background-${state.toLowerCase()})`
    });

    /* =============================
   Concurrency state (NEW)
============================= */
    const runningCount = ref(0);
    const totalCount = ref(0);
    const runningCountSet = ref(false);

    // NEW: refresh key for auto reload
    const refreshKey = ref(0);

    const setRunningCount = (count: number | { runningCount: number; totalCount: number }) => {
        if (typeof count === "object") {
            runningCount.value = count.runningCount;
            totalCount.value = count.totalCount;
        } else {
            runningCount.value = count;
            totalCount.value = count;
        }
        runningCountSet.value = true;
    };

    const progress = computed(() => {
        if (!execution?.concurrency?.limit) return 0;
        return (runningCount.value / execution.concurrency.limit) * 100;
    });

    //Auto refresh every 30s (FIXEd)
    let interval: any;

    onMounted(() => {
        interval = setInterval(() => {
            refreshKey.value++;
        }, 30000);
    });

    onUnmounted(() => {
        if (interval) clearInterval(interval);
    });
</script>

<style scoped lang="scss">
.queued {
    margin-top: -2rem;
}

p {
    color: var(--ks-content-secondary);
}

:deep(.el-card) {
    background-color: var(--ks-background-panel);
}

:deep(.el-progress) {
    .el-progress-bar,
    .el-progress-bar__outer,
    .el-progress-bar__inner {
        border-radius: var(--bs-border-radius);
    }
}
</style>
