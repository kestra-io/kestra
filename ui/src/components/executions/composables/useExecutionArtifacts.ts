import {computed, ref, watch, type InjectionKey} from "vue"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import type {FlowForExecution} from "@kestra-io/kestra-sdk"
import {useExecutionsStore} from "../../../stores/executions"
import {useFlowStore} from "../../../stores/flow"
import {useFederatedModule} from "../../../remoteComponents/useFederatedModule"
import {getAllTasks} from "../../../utils/flowUtils"

export interface ExecutionArtifact {
    key: string
    taskId: string
    taskType: string
    cls: string
    title: string
    description?: string
}

export const EXECUTION_ARTIFACTS_KEY: InjectionKey<ReturnType<typeof useExecutionArtifacts>> = Symbol("execution-artifacts")

export function useExecutionArtifacts(flow?: () => FlowForExecution | undefined) {
    const executionsStore = useExecutionsStore()
    const currentFlow = flow ?? (() => executionsStore.flow)
    const {RemoteComponent, taskAdditionalInfoRemote, manifestReady, hasResolvedComponent, hasFailedComponent, resolveRemoteComponent, subtitleResolvers} = useFederatedModule("execution-artifact")

    const flowStore = useFlowStore()
    const executedSource = ref<string>()

    const executedRevision = () => {
        if (flow) {
            const given = flow()
            return given ? {namespace: given.namespace, id: given.id, revision: given.revision} : undefined
        }
        const execution = executionsStore.execution
        return execution ? {namespace: execution.namespace, id: execution.flowId, revision: execution.flowRevision} : undefined
    }

    watch(() => {
        const executed = executedRevision()
        return executed ? `${executed.namespace}/${executed.id}/${executed.revision}` : undefined
    }, async (key) => {
        executedSource.value = undefined
        const executed = executedRevision()
        if (!key || !executed) return
        const loaded = await flowStore.loadFlow({
            namespace: executed.namespace,
            id: executed.id,
            revision: executed.revision?.toString(),
            store: false,
            optional: true,
        })
        if (key === `${executed.namespace}/${executed.id}/${executed.revision}`) executedSource.value = loaded?.source
    }, {immediate: true})

    const sourceTaskById = computed(() => {
        if (!executedSource.value) return {}
        const parsed = YAML_UTILS.parse<Record<string, unknown>>(executedSource.value, false)
        return Object.fromEntries(
            ["tasks", "errors", "finally", "afterExecution"]
                .flatMap((section) => getAllTasks(parsed?.[section]))
                .map((task) => [task.id, task]),
        )
    })

    const tasks = computed(() => getAllTasks(currentFlow()?.tasks).map((task) => ({...sourceTaskById.value[task.id], ...task})))

    const ownerTypesOf = (task: {type: string, taskRunner?: {type?: string}}) =>
        [...new Set([task.type, task.taskRunner?.type].filter((type): type is string => !!type))]

    const taskTypes = computed(() => [...new Set(tasks.value.flatMap(ownerTypesOf))].sort())

    watch(taskTypes, (types) => {
        if (currentFlow()) resolveRemoteComponent(types.map((cls) => ({cls, version: undefined})))
    }, {immediate: true})

    const artifacts = computed<ExecutionArtifact[]>(() => tasks.value.flatMap((task) =>
        ownerTypesOf(task).flatMap((owner) =>
            Object.entries(taskAdditionalInfoRemote.value)
                .filter(([key]) => key.startsWith(`${owner}/`))
                .map(([key, info]) => ({
                    key: `${task.id}:${key}`,
                    taskId: task.id,
                    taskType: key,
                    cls: task.type,
                    title: info?.title ?? key.slice(owner.length + 1),
                    description: info?.description,
                })),
        ),
    ))

    return {RemoteComponent, artifacts, tasks, subtitleResolvers, manifestReady, hasResolvedComponent, hasFailedComponent}
}
