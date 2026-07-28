<template>
    <KsCard>
        <div class="vueflow">
            <LowCodeEditor
                :key="execution.id"
                v-if="execution && flowGraph"
                :flowId="execution.flowId"
                :namespace="execution.namespace"
                :flowGraph="flowGraph"
                :source="flowStore.flow?.source"
                :execution="execution"
                :expandedSubflows="expandedSubflows"
                :horizontalDefault="horizontalDefault"
                isReadOnly
                @follow="$emit('follow', $event)"
                viewType="topology"
                @expand-subflow="onExpandSubflow"
            />
            <div v-else-if="loading" v-ks-loading="true" style="height:100%;position:relative" />
            <KsAlert v-else type="warning" :closable="false">
                {{ $t("unable to generate graph") }}
            </KsAlert>
        </div>
    </KsCard>
</template>
<script setup lang="ts">
    import {ref, computed, watch, onMounted, onUnmounted} from "vue"
    import throttle from "lodash/throttle"
    import {stringUtils, State, levelToRequestParams} from "@kestra-io/design-system"
    import LowCodeEditor from "../inputs/LowCodeEditor.vue"
    import {useExecutionsStore} from "../../stores/executions"
    import {useFlowStore} from "../../stores/flow"

    withDefaults(defineProps<{
        horizontalDefault?: boolean
    }>(), {
        horizontalDefault: undefined,
    })

    const emit = defineEmits<{
        follow: [event: unknown]
    }>()

    const executionsStore = useExecutionsStore()
    const flowStore = useFlowStore()

    // FIXME: any - execution and flowGraph are untyped domain objects from the store
    const execution = computed(() => executionsStore.execution as any) // FIXME: any
    const flowGraph = computed(() => executionsStore.flowGraph)

    const loading = ref(true)
    const previousExecutionId = ref<string | undefined>(undefined)
    const expandedSubflows = ref<string[]>([])
    const previousExpandedSubflows = ref<string[]>([])
    // FIXME: any - SSE objects don't have a consistent type in this codebase
    const sseBySubflow = ref<Record<string, any>>({}) // FIXME: any

    // Live lifecycle-step progress (see RunContext#emitProgress) rides the existing follow-logs
    // SSE as a typed field, so plugin topology-details slots can track per-step progress in real
    // time — metrics/outputs only materialise once the task run completes.
    let progressSSE: EventSource | undefined
    // followLogs() resolves asynchronously; if the component unmounts in that window, the
    // EventSource would land in a ref no one closes again. Guard against it explicitly instead
    // of relying on onUnmounted alone.
    let unmounted = false

    function closeProgressSSE() {
        progressSSE?.close()
        progressSSE = undefined
    }

    function followProgress() {
        closeProgressSSE()
        const id = execution.value?.id
        if (!id) return
        executionsStore.followLogs({id, params: levelToRequestParams({value: "INFO", direction: "min"})}).then((sse: EventSource) => {
            if (unmounted) {
                sse.close()
                return
            }
            progressSSE = sse
            sse.onmessage = (event: MessageEvent) => {
                if (event.lastEventId === "start") return
                const data = JSON.parse(event.data)
                if (!data.progress) return
                executionsStore.addProgressEvent({
                    taskId: data.taskId,
                    taskRunId: data.taskRunId,
                    step: data.progress,
                    timestamp: data.timestamp,
                })
            }
            // No onerror handler: closing here on a transient drop is what previously froze the
            // stepper (kestra-io/kestra#16982's leak fear doesn't apply — closeProgressSSE()
            // below still runs once the execution terminates). Let EventSource auto-reconnect;
            // the historical replay it gets on reconnect is idempotent thanks to the dedup in
            // addProgressEvent, so a reconnect can only ever catch up, never regress.
        })
    }

    const isExecutionRunning = computed(() => {
        const current = execution.value?.state?.current
        return !!current && State.isRunning(current)
    })

    watch(
        [() => execution.value?.id, isExecutionRunning],
        ([id, running]) => {
            if (id && running) followProgress()
            else closeProgressSSE()
        },
        {immediate: true},
    )

    const throttledExecutionUpdate = throttle(function(subflow: string, subflowExecution: any) { // FIXME: any
        const previousExecution = executionsStore.subflowsExecutions[subflow]
        executionsStore.addSubflowExecution({
            subflow,
            execution: subflowExecution,
        })

        // add subflow execution id to graph
        if (previousExecution === undefined) {
            loadGraph(true)
        }
    }, 500)

    watch(execution, () => {
        loadData()
    })

    onMounted(() => {
        loadData()
    })

    onUnmounted(() => {
        unmounted = true
        Object.keys(sseBySubflow.value).forEach(closeSSE)
        closeProgressSSE()
    })

    function closeSSE(subflow: string) {
        sseBySubflow.value[subflow].close()
        delete sseBySubflow.value[subflow]
        executionsStore.removeSubflowExecution(subflow)
    }

    function loadData() {
        loadGraph()
        loadFlowSource()
    }

    function loadFlowSource() {
        const exec = execution.value
        if (!exec || flowStore.flow?.id === exec.flowId && flowStore.flow?.namespace === exec.namespace) return
        flowStore.loadFlow({namespace: exec.namespace, id: exec.flowId})
    }

    function loadGraph(force?: boolean) {
        loading.value = true

        if (execution.value && (force || (flowGraph.value === undefined || previousExecutionId.value !== execution.value.id))) {
            previousExecutionId.value = execution.value.id
            executionsStore.loadAugmentedGraph({
                id: execution.value.id,
                params: {
                    subflows: expandedSubflows.value,
                },
            }).catch(() => {
                expandedSubflows.value = previousExpandedSubflows.value

                handleSubflowsSSE()
            }).finally(() => {
                loading.value = false
            })
        } else {
            loading.value = false
        }
    }

    function onExpandSubflow(newExpandedSubflows: string[]) {
        previousExpandedSubflows.value = expandedSubflows.value
        expandedSubflows.value = newExpandedSubflows

        handleSubflowsSSE()
    }

    function handleSubflowsSSE() {
        Object.keys(sseBySubflow.value).filter(subflow => !expandedSubflows.value.includes(subflow))
            .forEach(closeSSE)

        // resolve parent subflows' execution first
        const subflowsWithoutSSE = expandedSubflows.value.filter(subflow => !(subflow in sseBySubflow.value))
            .sort((a, b) => (a.match(/\./g)?.length || 0) - (b.match(/\./g)?.length || 0))

        subflowsWithoutSSE.forEach(subflow => {
            addSSE(subflow, true)
        })
    }

    function delaySSE(generateGraphBeforeDelay: boolean, subflow: string) {
        if (generateGraphBeforeDelay) {
            loadGraph(true)
        }
        setTimeout(() => addSSE(subflow), 500)
    }

    function addSSE(subflow: string, generateGraphOnWaiting?: boolean) {
        let parentExecution = execution.value

        const parentSubflows = expandedSubflows.value.filter(expandedSubflow => subflow.includes(expandedSubflow + "."))
            .sort((s1, s2) => s2.length - s1.length)

        if (parentSubflows.length > 0) {
            parentExecution = executionsStore.subflowsExecutions[parentSubflows[0]]
        }

        if (!parentExecution) {
            delaySSE(!!generateGraphOnWaiting, subflow)
            return
        }

        const taskIdMatchingTaskrun = parentExecution.taskRunList
            .filter((taskRun: {taskId: string}) => taskRun.taskId === stringUtils.afterLastDot(subflow))?.[0]
        const executionId = taskIdMatchingTaskrun?.outputs?.executionId

        if (!executionId) {
            if (taskIdMatchingTaskrun?.state?.current === State.SUCCESS) {
                // Generating more than 1 subflow execution, we're not showing anything
                loadGraph(true)
                return
            }

            delaySSE(!!generateGraphOnWaiting, subflow)
            return
        }

        if (unmounted) {
            return
        }

        // subscribeToExecution (not followExecution) is required here — followExecution treats
        // its target as *the* displayed execution: it nulls executionsStore.execution, closes,
        // and overwrites the shared subscription handle. Expanding a subflow would silently kill
        // the parent execution's own live stream (see useExecutionRoot.ts) and, with several
        // subflows expanded, each new one would kill the previous one's tracking too. A dedicated
        // subscription keeps each subflow's stream independent.
        sseBySubflow.value[subflow] = executionsStore.subscribeToExecution(executionId, {
            onExecution: (subflowExecution) => throttledExecutionUpdate(subflow, subflowExecution),
            onEnd: () => {
                throttledExecutionUpdate.flush()
                closeSubExecutionSSE(subflow)
            },
        })
    }

    function closeSubExecutionSSE(subflow: string) {
        const sse = sseBySubflow.value[subflow]
        if (sse) {
            sse.close()
            delete sseBySubflow.value[subflow]
        }
    }
</script>
<style scoped lang="scss">
    .kel-card {
        height: var(--topology-height, calc(100vh - 174px));
        position: relative;
        background-color: var(--ks-bg-base);

        :deep(.kel-card__body) {
            height: 100%;
            display: flex;
            padding: 0;
        }
    }

    .vueflow {
        height: 100%;
        width: 100%;
    }
</style>
