import {ref, watch, type Ref} from "vue"
import type {MetricEntry} from "@kestra-io/kestra-sdk"

import type {JSONProperty} from "../plugins/schema/utils/schemaUtils"
import type {Execution} from "../../stores/executions"
import {useFlowStore, type Flow} from "../../stores/flow"
import {usePluginsStore} from "../../stores/plugins"
import {getAllTasks} from "../../utils/flowUtils"
import {handled} from "../../utils/kestraHttp"

type MetricRow = Pick<MetricEntry, "name" | "taskId">

export function collectTaskTypes(flow: Pick<Flow, "tasks" | "errors" | "finally" | "afterExecution">): Map<string, string> {
    const tasks = getAllTasks([flow.tasks, flow.errors, flow.finally, flow.afterExecution])
    return new Map(tasks.map(task => [task.id, task.type]))
}

export function findMetricDescription(metrics: JSONProperty[] | undefined, name: string): string | undefined {
    return metrics?.find(metric => metric.name === name)?.description || undefined
}

export function useMetricDescriptions(execution: () => Execution | undefined, metrics: Ref<MetricRow[] | undefined>) {
    const flowStore = useFlowStore()
    const pluginsStore = usePluginsStore()

    const taskTypes = ref(new Map<string, string>())
    const pluginMetrics = ref(new Map<string, JSONProperty[] | undefined>())

    const pluginTypeOf = (taskId: string | null | undefined) => taskId ? taskTypes.value.get(taskId) : undefined

    watch(
        [() => execution()?.namespace, () => execution()?.flowId, () => execution()?.flowRevision],
        async ([namespace, id, revision], _previous, onCleanup) => {
            let superseded = false
            onCleanup(() => superseded = true)

            taskTypes.value = new Map()
            if (!namespace || !id) {
                return
            }

            try {
                const flow = await flowStore.loadFlow({namespace, id, revision: revision?.toString(), allowDeleted: true, store: false})
                if (!superseded) {
                    taskTypes.value = collectTaskTypes(flow)
                }
            } catch (error) {
                handled(error)
            }
        },
        {immediate: true},
    )

    watch([metrics, taskTypes], () => {
        const pluginTypes = new Set((metrics.value ?? []).map(metric => pluginTypeOf(metric.taskId)))

        for (const type of pluginTypes) {
            if (!type || pluginMetrics.value.has(type)) {
                continue
            }

            pluginMetrics.value.set(type, undefined)
            pluginsStore.load({cls: type, commit: false})
                .then(plugin => pluginMetrics.value.set(type, plugin.schema.properties?.$metrics))
                .catch(handled)
        }
    }, {immediate: true})

    return (metric: MetricRow) => {
        const type = pluginTypeOf(metric.taskId)
        return type ? findMetricDescription(pluginMetrics.value.get(type), metric.name) : undefined
    }
}
