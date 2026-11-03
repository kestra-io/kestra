import {computed, watch, type InjectionKey} from "vue"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
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

export function useExecutionArtifacts() {
    const executionsStore = useExecutionsStore()
    const {RemoteComponent, taskAdditionalInfoRemote, manifestReady, hasResolvedComponent, hasFailedComponent, resolveRemoteComponent, subtitleResolvers} = useFederatedModule("execution-artifact")

    const flowStore = useFlowStore()

    // The execution's flow comes without the properties an artifact needs, such as `taskRunner`. They are
    // read back from the flow source, as the topology does, when it is the source of this execution's flow.
    const sourceTaskById = computed(() => {
        const flow = executionsStore.flow
        const source = flowStore.flow?.source
        const sameFlow = flow && flowStore.flow?.id === flow.id && flowStore.flow?.namespace === flow.namespace
        if (!source || !sameFlow) return {}
        const parsed = YAML_UTILS.parse<Record<string, unknown>>(source, false)
        return Object.fromEntries(
            ["tasks", "errors", "finally", "afterExecution"]
                .flatMap((section) => getAllTasks(parsed?.[section]))
                .map((task) => [task.id, task]),
        )
    })

    const tasks = computed(() => getAllTasks(executionsStore.flow?.tasks).map((task) => ({...sourceTaskById.value[task.id], ...task})))

    // A task runner ships its own artifacts, so its type is resolved and matched like the task's own.
    const ownerTypesOf = (task: {type: string, taskRunner?: {type?: string}}) =>
        [...new Set([task.type, task.taskRunner?.type].filter((type): type is string => !!type))]

    const taskTypes = computed(() => [...new Set(tasks.value.flatMap(ownerTypesOf))].sort())

    watch(taskTypes, (types) => {
        if (executionsStore.flow) resolveRemoteComponent(types.map((cls) => ({cls, version: undefined})))
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
