<template>
    <KsCard>
        <div class="vueflow">
            <LowCodeEditor
                ref="editor"
                :key="execution.id"
                v-if="execution && flowGraph"
                :flowId="execution.flowId"
                :namespace="execution.namespace"
                :flowGraph="flowGraph"
                :source="flowStore.flow?.source"
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
    import {ref, computed, watch, onMounted, onUnmounted, useTemplateRef} from "vue"
    import {stringUtils, State, throttle} from "@kestra-io/design-system"
    import {isLoopTaskType} from "@kestra-io/topology"
    import LowCodeEditor from "../inputs/LowCodeEditor.vue"
    import {useExecutionsStore, type Execution} from "../../stores/executions"
    import {deepestEnclosingPath} from "../../utils/loopScope"
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

    const editor = useTemplateRef<InstanceType<typeof LowCodeEditor>>("editor")

    defineExpose({focusTask: (taskId: string) => editor.value?.focusTask(taskId)})

    const execution = computed(() => executionsStore.execution)
    const flowGraph = computed(() => executionsStore.scopedFlowGraph)

    const loading = ref(true)
    const previousExecutionId = ref<string | undefined>(undefined)
    const expandedSubflows = ref<string[]>([])
    const previousExpandedSubflows = ref<string[]>([])
    const sseBySubflow = ref<Record<string, {close: () => void}>>({})
    let unmounted = false
    const sseRetries = new Map<string, ReturnType<typeof setTimeout>>()

    type TaskRun = NonNullable<Execution["taskRunList"]>[number]

    const CHILD_PAGE_SIZE = 100
    const MAX_LOOKUP_DELAY = 10_000

    const childLookups = new Map<string, number>()
    const lookupAttempts = new Map<string, number>()

    const throttledExecutionUpdate = throttle(function(subflow: string, subflowExecution: Execution) {
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
        sseRetries.forEach((retry) => clearTimeout(retry))
        Object.keys(sseBySubflow.value).forEach(closeSSE)
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
        flowStore.loadFlow({namespace: exec.namespace, id: exec.flowId, optional: true})
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

    function delaySSE(generateGraphBeforeDelay: boolean, subflow: string, delay = 500) {
        if (generateGraphBeforeDelay) {
            loadGraph(true)
        }
        clearTimeout(sseRetries.get(subflow))
        sseRetries.set(subflow, setTimeout(() => {
            sseRetries.delete(subflow)
            addSSE(subflow)
        }, delay))
    }

    function addSSE(subflow: string, generateGraphOnWaiting?: boolean) {
        if (unmounted) {
            return
        }

        const parentPath = deepestEnclosingPath(subflow, [
            ...expandedSubflows.value.filter(expandedSubflow => subflow.includes(expandedSubflow + ".")),
            ...loopUids.value,
        ])

        if (parentPath && loopUids.value.includes(parentPath) && !executionsStore.scopedIterations[parentPath]) {
            return
        }

        const parentExecution: Execution | undefined = !parentPath
            ? execution.value
            : executionsStore.subflowsExecutions[parentPath] ?? executionsStore.scopedIterations[parentPath]

        if (!parentExecution) {
            delaySSE(!!generateGraphOnWaiting, subflow)
            return
        }

        const taskRun = (parentExecution.taskRunList ?? []).find(run => run.taskId === stringUtils.afterLastDot(subflow))
        if (!taskRun) {
            delaySSE(!!generateGraphOnWaiting, subflow)
            return
        }

        const lookup = (childLookups.get(subflow) ?? 0) + 1
        childLookups.set(subflow, lookup)
        const isTaskRunning = !taskRun.state?.current || !!State.isRunning(taskRun.state.current)
        findChildExecutionId(taskRun, taskRun.executionId ?? parentExecution.id, isTaskRunning)
            .catch(() => undefined)
            .then(executionId => {
                if (unmounted || childLookups.get(subflow) !== lookup || !expandedSubflows.value.includes(subflow) || subflow in sseBySubflow.value) {
                    return
                }

                if (executionId) {
                    lookupAttempts.delete(subflow)
                    subscribeToSubflow(subflow, executionId)
                } else if (!isTaskRunning) {
                    lookupAttempts.delete(subflow)
                    loadGraph(true)
                } else {
                    const attempts = lookupAttempts.get(subflow) ?? 0
                    lookupAttempts.set(subflow, attempts + 1)
                    delaySSE(!!generateGraphOnWaiting, subflow, Math.min(500 * 2 ** attempts, MAX_LOOKUP_DELAY))
                }
            })
    }

    // A running subflow's child is among the newest, so only a finished task run pages through older children.
    async function findChildExecutionId(taskRun: TaskRun, parentExecutionId: string, isTaskRunning: boolean): Promise<string | undefined> {
        const kind = execution.value?.kind
        for (let page = 1; ; page++) {
            const response = await executionsStore.findExecutions({
                "filters[triggerExecutionId][EQUALS]": parentExecutionId,
                "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": taskRun.state?.startDate,
                "filters[kind][IN]": kind && kind !== "NORMAL" ? kind : undefined,
                sort: "state.startDate:desc",
                page,
                size: CHILD_PAGE_SIZE,
                commit: false,
            })
            const results = response.results ?? []
            const child = results.find(result => result.trigger?.variables?.taskRunId === taskRun.id)
            if (child || isTaskRunning || results.length < CHILD_PAGE_SIZE) {
                return child?.id
            }
        }
    }

    function subscribeToSubflow(subflow: string, executionId: string) {
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

    const loopUids = computed(() => (executionsStore.flowGraph?.clusters ?? [])
        .map(({cluster}) => cluster.taskNode)
        .filter((taskNode) => taskNode && isLoopTaskType(taskNode.task.type))
        .map((taskNode) => taskNode!.uid))

    const scopedIterationsKey = computed(() => Object.entries(executionsStore.scopedIterations)
        .map(([lane, iteration]) => `${lane}=${iteration.id}`)
        .sort()
        .join("|"))

    watch(scopedIterationsKey, () => {
        const subflowsInLoops = expandedSubflows.value.filter(subflow => loopUids.value.some(uid => subflow.startsWith(uid + ".")))
        if (!subflowsInLoops.length) return

        subflowsInLoops.forEach(subflow => {
            clearTimeout(sseRetries.get(subflow))
            sseRetries.delete(subflow)
            closeSubExecutionSSE(subflow)
            executionsStore.removeSubflowExecution(subflow)
        })
        loadGraph(true)
        subflowsInLoops.forEach(subflow => addSSE(subflow))
    })

    function closeSubExecutionSSE(subflow: string) {
        const sse = sseBySubflow.value[subflow]
        if (sse) {
            sse.close()
            delete sseBySubflow.value[subflow]
        }
    }
</script>
<style scoped>
    .kel-card {
        /* A fixed height left most of a tall viewport unused: */
        /* https://github.com/kestra-io/kestra/issues/19685. */
        height: 100%;
        position: relative;
        background-color: var(--ks-bg-base);

        & :deep(.kel-card__body) {
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
