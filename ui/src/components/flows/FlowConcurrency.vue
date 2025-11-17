<template>
    <template v-if="flowStore.flow?.concurrency">
        <div v-if="totalCount > 0 || !runningCountSet" :class="{'d-none': !runningCountSet}">
            <el-card class="mb-3">
                <div class="row mb-3">
                    <span class="col d-flex align-items-center">
                        <h5 class="m-3">RUNNING</h5> {{ runningCount }}/{{ flowStore.flow?.concurrency?.limit }} {{ $t('active-slots') }}
                    </span>
                    <span class="col d-flex justify-content-end align-items-center">
                        {{ $t('behavior') }}: <Status class="mx-2" :status="flowStore.flow?.concurrency?.behavior" size="small" />
                    </span>
                </div>
                <div class="progressbar mb-3">
                    <el-progress :stroke-width="16" color="#5BB8FF" :percentage="progress" :showText="false" />
                </div>
            </el-card>
            <el-card>
                <Executions
                    :restoreUrl="false"
                    :topbar="false"
                    :namespace="flowStore.flow?.namespace"
                    :flowId="flowStore.flow?.id"
                    isConcurrency
                    :statuses="[State.QUEUED, State.RUNNING, State.PAUSED]"
                    @state-count="setRunningCount"
                    filter
                />
            </el-card>
        </div>
        <Empty v-else type="concurrency_executions" />
    </template>
    <Empty v-else type="concurrency_limit" />
</template>

<script setup lang="ts">
    import {ref, computed} from "vue";
    import Executions from "../executions/Executions.vue";
    import Empty from "../layout/empty/Empty.vue";
    import {State, Status} from "@kestra-io/ui-libs";
    import {useFlowStore} from "../../stores/flow";

    defineOptions({inheritAttrs: false});

    const flowStore = useFlowStore();

    const runningCount = ref(0);
    const totalCount = ref(0);
    const runningCountSet = ref(false);

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
        if (!flowStore.flow?.concurrency) return 0;
        return runningCount.value / flowStore.flow.concurrency.limit * 100;
    });
</script>

<style scoped lang="scss">
    .img-size {
        max-width: 200px;
    }
    .bg-purple {
        height: 100%;
        width: 100%;
    }
    h5 {
        font-weight: bold;
        margin-left: 0 !important;
    }

    :deep(.el-progress) {
        .el-progress-bar, .el-progress-bar__outer, .el-progress-bar__inner {
            border-radius: var(--bs-border-radius);
        }
    }

    :deep(.el-card) {
        background-color: var(--ks-background-panel);
    }
</style>