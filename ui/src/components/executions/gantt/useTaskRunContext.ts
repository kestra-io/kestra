import {computed, ref, watch, type Ref} from "vue"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import {flow as fetchFlow} from "@kestra-io/kestra-sdk/flows"
import {useExecutionsStore, type Execution} from "../../../stores/executions"
import type {ExecutionTaskRun} from "../executionTaskRun"
import {TASK_SECTIONS} from "../../no-code/blocks/blockSections"
import type {StructuralNode} from "./types"
import {
    createTaskRunNode,
    indexTaskRuns,
    iterationAncestors,
    owningIteration,
    taskRunAncestors,
    useGanttTree,
    type TaskRunNode,
} from "./ganttTree"

const MAX_STRUCTURAL_NEIGHBORS = 6
const OUTPUT_REFERENCE = /\boutputs(?:\[['"]|\.)([A-Za-z0-9_-]+)/g

type FlowWithSource = Awaited<ReturnType<typeof fetchFlow>>

const startTime = (taskRun: ExecutionTaskRun): number => new Date(taskRun.state.histories[0].date).getTime()

export function useTaskRunContext(node: () => TaskRunNode, flowEnabled: Ref<boolean>) {
    const tree = useGanttTree()
    const executionsStore = useExecutionsStore()

    const isRootExecution = computed(() => node().executionId === executionsStore.execution?.id)
    const execution = computed<Execution | undefined>(() =>
        isRootExecution.value ? executionsStore.execution : tree.executionEntry(node().executionId).execution,
    )

    watch(
        () => node().executionId,
        (executionId) => {
            if (!isRootExecution.value) tree.loadExecution(executionId)
        },
        {immediate: true},
    )

    const taskRuns = computed(() =>
        ((execution.value?.taskRunList ?? []) as ExecutionTaskRun[]).filter(taskRun => (taskRun.state?.histories?.length ?? 0) > 0),
    )

    const flow = ref<FlowWithSource | undefined>(undefined)
    const flowLoading = ref(false)
    const flowError = ref(false)
    let loadedFlowKey: string | undefined
    let latestFlowFetch = 0

    const flowKey = computed(() => {
        const current = execution.value
        return current ? `${current.namespace}/${current.flowId}/${current.flowRevision}` : undefined
    })

    watch([flowKey, flowEnabled], async ([key, enabled]) => {
        const current = execution.value
        if (!enabled || !key || !current || key === loadedFlowKey) return

        const fetch = ++latestFlowFetch
        loadedFlowKey = key
        flowLoading.value = true
        flowError.value = false
        flow.value = undefined
        try {
            const loaded = await fetchFlow({
                namespace: current.namespace,
                id: current.flowId,
                revision: current.flowRevision,
                source: true,
            })
            if (fetch === latestFlowFetch) flow.value = loaded
        } catch {
            if (fetch === latestFlowFetch) {
                flowError.value = true
                loadedFlowKey = undefined
            }
        } finally {
            if (fetch === latestFlowFetch) flowLoading.value = false
        }
    }, {immediate: true})

    const rawBlock = (taskId: string): string | undefined => {
        const source = flow.value?.source
        if (!source) return undefined
        for (const section of TASK_SECTIONS) {
            const block = YAML_UTILS.extractBlock({source, section, key: taskId})
            if (block) return block
        }
        return undefined
    }

    const rawTaskBlock = computed(() => rawBlock(node().taskRun.taskId))
    const inputIds = computed(() => (flow.value?.inputs ?? []).map(input => input.id))
    const referencedOutputTaskIds = computed(() => {
        const matches = (rawTaskBlock.value ?? "").matchAll(OUTPUT_REFERENCE)
        return [...new Set([...matches].map(match => match[1]))]
    })

    const structuralNodes = computed<StructuralNode[]>(() => {
        const focused = node().taskRun
        const byId = indexTaskRuns(taskRuns.value)
        const parent = focused.parentTaskRunId ? byId.get(focused.parentTaskRunId) : undefined
        const children = taskRuns.value.filter(taskRun => taskRun.parentTaskRunId === focused.id)
        const siblings = taskRuns.value
            .filter(taskRun => taskRun.id !== focused.id && taskRun.parentTaskRunId === focused.parentTaskRunId)
            .sort((a, b) => Math.abs(startTime(a) - startTime(focused)) - Math.abs(startTime(b) - startTime(focused)))

        const nodes: StructuralNode[] = [{taskRun: focused, relation: "focused"}]
        const add = (taskRun: ExecutionTaskRun | undefined, relation: StructuralNode["relation"]) => {
            if (!taskRun || nodes.some(existing => existing.taskRun.id === taskRun.id) || nodes.length >= MAX_STRUCTURAL_NEIGHBORS) return
            nodes.push({taskRun, relation})
        }

        add(parent, "parent")
        children.forEach(child => add(child, "child"))
        siblings.forEach(sibling => add(sibling, "sibling"))
        return nodes
    })

    const structuralRawBlocks = computed<Record<string, string | undefined>>(() =>
        Object.fromEntries(structuralNodes.value.map(({taskRun}) => [taskRun.id, rawBlock(taskRun.taskId)])),
    )

    function selectTaskRun(taskRunId: string) {
        const target = taskRuns.value.find(taskRun => taskRun.id === taskRunId)
        if (!target || target.id === node().taskRun.id) return

        const iteration = owningIteration(node())
        const base = iteration ? iterationAncestors(iteration) : []
        const owner = {id: node().executionId, namespace: node().namespace, flowId: node().flowId}
        tree.select(
            createTaskRunNode(owner, target, tree.taskType(target.taskId), taskRunAncestors(indexTaskRuns(taskRuns.value), target, base)),
            {reveal: true},
        )
    }

    return {
        execution,
        taskRuns,
        flowLoading,
        flowError,
        rawTaskBlock,
        inputIds,
        referencedOutputTaskIds,
        structuralNodes,
        structuralRawBlocks,
        selectTaskRun,
    }
}
