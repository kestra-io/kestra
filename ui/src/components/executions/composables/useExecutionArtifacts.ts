import {computed, watch, type InjectionKey} from "vue"
import {useExecutionsStore} from "../../../stores/executions"
import {useFederatedModule} from "../../../remoteComponents/useFederatedModule"
import {getAllTasks} from "../../../utils/flowUtils"

export interface ExecutionArtifact {
    key: string
    taskId: string
    taskType: string
    title: string
    description?: string
}

export const EXECUTION_ARTIFACTS_KEY: InjectionKey<ReturnType<typeof useExecutionArtifacts>> = Symbol("execution-artifacts")

export function useExecutionArtifacts() {
    const executionsStore = useExecutionsStore()
    const {RemoteComponent, taskAdditionalInfoRemote, resolveRemoteComponent, subtitleResolvers} = useFederatedModule("execution-artifact")

    const tasks = computed(() => getAllTasks(executionsStore.flow?.tasks))

    const taskTypes = computed(() => [...new Set(tasks.value.map((task) => task.type))].sort())

    watch(taskTypes, (types) => resolveRemoteComponent(types.map((cls) => ({cls, version: undefined}))), {immediate: true})

    const artifacts = computed<ExecutionArtifact[]>(() => tasks.value.flatMap((task) =>
        Object.entries(taskAdditionalInfoRemote.value)
            .filter(([key]) => key.startsWith(`${task.type}/`))
            .map(([key, info]) => ({
                key: `${task.id}:${key}`,
                taskId: task.id,
                taskType: key,
                title: info?.title ?? key.slice(task.type.length + 1),
                description: info?.description,
            })),
    ))

    return {RemoteComponent, artifacts, tasks, subtitleResolvers}
}
