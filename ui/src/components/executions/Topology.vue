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
    import {useI18n} from "vue-i18n"
    import throttle from "lodash/throttle"
    import {stringUtils, State} from "@kestra-io/design-system"
    import LowCodeEditor from "../inputs/LowCodeEditor.vue"
    import {useExecutionsStore} from "../../stores/executions"
    import {useFlowStore} from "../../stores/flow"

    const emit = defineEmits<{
        follow: [event: unknown]
    }>()

    const {t} = useI18n()
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

    const throttledExecutionUpdate = throttle(function(subflow: string, executionEvent: MessageEvent) {
        const previousExecution = executionsStore.subflowsExecutions[subflow]
        executionsStore.addSubflowExecution({
            subflow,
            execution: JSON.parse(executionEvent.data),
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
        Object.keys(sseBySubflow.value).forEach(closeSSE)
    })

    function closeSSE(subflow: string) {
        sseBySubflow.value[subflow].close()
        delete sseBySubflow.value[subflow]
        executionsStore.removeSubflowExecution(subflow)
    }

    function loadData() {
        loadGraph()
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

        executionsStore.followExecution({id: executionId}, t)
            .then((sse: {onmessage: ((event: MessageEvent) => void) | null; close: () => void}) => {
                sseBySubflow.value[subflow] = sse
                sse.onmessage = (executionEvent: MessageEvent) => {
                    const isEnd = executionEvent && executionEvent.lastEventId === "end"
                    if (isEnd) {
                        closeSubExecutionSSE(subflow)
                    }
                    // we are receiving a first "fake" event to force initializing the connection: ignoring it
                    if (executionEvent.lastEventId !== "start") {
                        throttledExecutionUpdate(subflow, executionEvent)
                    }
                    if (isEnd) {
                        throttledExecutionUpdate.flush()
                    }
                }
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
        height: calc(100vh - 174px);
        position: relative;

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
