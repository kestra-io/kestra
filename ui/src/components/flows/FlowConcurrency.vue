<template>
    <template v-if="hasConcurrency">
        <div v-if="totalCount > 0 || !runningCountSet" :class="{'d-none': !runningCountSet}">
            <el-card class="mb-3">
                <div class="row mb-3">
                    <span class="col d-flex align-items-center">
                        <h5 class="m-3">RUNNING</h5> {{ runningCount }}/{{ limit }} {{ $t('active-slots') }}
                    </span>
                    <span class="col d-flex justify-content-end align-items-center">
                        {{ $t('behavior') }}: <Status class="mx-2" :status="behavior ?? ''" size="small" />
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
                    :namespace="namespace ?? ''"
                    :flowId="flowId ?? ''"
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
    import {computed, ref} from "vue";
    import Executions from "../executions/Executions.vue";
    import Empty from "../layout/empty/Empty.vue";
    import {State} from "@kestra-io/ui-libs";
    import Status from "../Status.vue";
    import {useFlowStore} from "../../stores/flow";

    defineOptions({inheritAttrs: false})

    defineEmits<{
        (e: "expand-subflow", payload?: unknown): void
    }>()

    const flowStore = useFlowStore()
    
    // Interfaces
    interface FlowLike {
        id?: string
        namespace?: string
        concurrency?: {
            limit?: number
            behavior?: string
        }
    }

    interface RunningCountPayload {
        runningCount: number
        totalCount: number
    }

    // Type guards
    function isFlowLike(flow: unknown): flow is FlowLike {
        if (flow === null || typeof flow !== "object") return false
        const f = flow as Record<string, unknown>
        const hasId = !("id" in f) || typeof f.id === "string"
        const hasNamespace = !("namespace" in f) || typeof f.namespace === "string"
        const concurrency = (f as any).concurrency
        const hasConcurrency = concurrency === undefined || (
            typeof concurrency === "object" &&
            (concurrency?.limit === undefined || typeof concurrency.limit === "number") &&
            (concurrency?.behavior === undefined || typeof concurrency.behavior === "string")
        )
        return hasId && hasNamespace && hasConcurrency
    }

    // Reactive state
    const runningCount = ref<number>(0)
    const totalCount = ref<number>(0)
    const runningCountSet = ref<boolean>(false)

    // Computed properties
    const currentFlow = computed<FlowLike | undefined>(() => {
        const flow = (flowStore as any).flow
        return isFlowLike(flow) ? flow : undefined
    })

    const hasConcurrency = computed(() => Boolean(currentFlow.value?.concurrency))
    const limit = computed(() => currentFlow.value?.concurrency?.limit ?? 0)
    const behavior = computed(() => currentFlow.value?.concurrency?.behavior ?? undefined)
    const flowId = computed(() => currentFlow.value?.id ?? undefined)
    const namespace = computed(() => currentFlow.value?.namespace ?? undefined)
    const progress = computed(() => {
        const max = limit.value
        if (!max || max <= 0) return 0
        const raw = Number(runningCount.value)
        const safe = Number.isFinite(raw) && raw > 0 ? raw : 0
        const clamped = Math.max(0, Math.min(safe, max))
        return Math.min(100, (clamped / max) * 100)
    })

    // Functions
    function setRunningCount(count: number | RunningCountPayload) {
        if (typeof count === "object") {
            runningCount.value = count.runningCount
            totalCount.value = count.totalCount
        } else {
            runningCount.value = totalCount.value = count
        }
        runningCountSet.value = true
    }
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