import {computed, ref, watch, type Ref} from "vue"
import {useI18n} from "vue-i18n"
import {useRoute} from "vue-router"
import type {KsBreadcrumbItem} from "@kestra-io/design-system"
import {loadIterationExecution} from "../utils/loopIterations"
import {
    iterationsListFilters,
    iterationsListLineage,
    resolveLoopLineage,
    type LineageExecution,
    type LoopLineageLevel,
} from "../utils/loopLineage"
import {useNamespaceBreadcrumb} from "./useNamespaceBreadcrumb"

interface IterationsParent {
    namespace: string;
    flowId: string;
    levels: LoopLineageLevel[];
}

function useLatest<T>(key: () => string | undefined, load: () => Promise<T | undefined>): Ref<T | undefined> {
    const result = ref<T>()
    let sequence = 0

    watch(key, async (current) => {
        const seq = ++sequence
        result.value = undefined
        if (!current) return
        const loaded = await load().catch(() => undefined)
        if (seq === sequence) result.value = loaded
    }, {immediate: true})

    return result
}

export function useLoopLineage(execution: () => LineageExecution | undefined) {
    const key = () => {
        const current = execution()
        return current?.loopRun ? `${current.id}|${current.parentId}|${current.loopRun.taskRunId}` : undefined
    }
    return useLatest<LoopLineageLevel[]>(key, () => resolveLoopLineage(execution()!))
}

export function useIterationsListInfo(enabled: () => boolean) {
    const {t} = useI18n()
    const route = useRoute()

    const filters = computed(() => enabled() ? iterationsListFilters(route.query) : undefined)
    const parent = useLatest<IterationsParent>(() => filters.value?.parentId, async () => {
        const execution = await loadIterationExecution(filters.value!.parentId)
        const levels = await resolveLoopLineage(execution)
        return {namespace: execution.namespace, flowId: execution.flowId, levels}
    })

    const namespaceBreadcrumb = useNamespaceBreadcrumb(() => parent.value?.namespace, {
        tab: "executions",
        root: {label: t("executions"), link: {name: "executions/list"}, scope: t("namespaces")},
    })

    return computed<{title: string; breadcrumb?: KsBreadcrumbItem[]} | undefined>(() => {
        if (!filters.value) return undefined
        if (!parent.value) return {title: t("iterations")}
        const {namespace, flowId, levels} = parent.value
        return {
            title: t("iterations"),
            breadcrumb: [
                ...namespaceBreadcrumb.value,
                {label: flowId, link: {name: "flows/update", params: {namespace, id: flowId}}},
                ...iterationsListLineage(filters.value.parentId, levels, filters.value.taskId, {namespace, flowId, t}),
            ],
        }
    })
}
