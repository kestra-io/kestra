import {ref, computed, onMounted, onUnmounted, watch} from "vue"
import {useRoute} from "vue-router"
import {useI18n} from "vue-i18n"
import type {KsBreadcrumbItem} from "@kestra-io/design-system"

import {useFlowStore} from "../../../stores/flow"
import {useExecutionsStore} from "../../../stores/executions"
import {useNamespaceBreadcrumb} from "../../../composables/useNamespaceBreadcrumb"
import {EXECUTION_PARENT_ROUTE, EXECUTION_TAB_ROUTES} from "../executionTabs"
import {isExecutionTabEnabled} from "override/components/executions/executionTabsExtension"

const MAX_CRUMB_VALUE_LENGTH = 20

function formatLoopCrumbLabel(taskId: string | undefined, value: string | undefined, index: number | undefined): string {
    const raw = value ?? (index !== undefined ? String(index) : "")
    const displayValue = raw.length > MAX_CRUMB_VALUE_LENGTH ? `${raw.slice(0, MAX_CRUMB_VALUE_LENGTH)}…` : raw
    return taskId ? `${taskId} (${displayValue})` : displayValue
}

export function useExecutionRoot() {
    const {t} = useI18n()
    const route = useRoute()

    const flowStore = useFlowStore()
    const executionsStore = useExecutionsStore()

    const dependenciesCount = ref<number>()
    const previousExecutionId = ref<string>()

    const namespaceBreadcrumb = useNamespaceBreadcrumb(() => route.params.namespace?.toString(), {
        tab: "executions",
        root: {label: t("executions"), link: {name: "executions/list"}, scope: t("namespaces")},
    })

    const routeInfo = computed(() => {
        const ns = route.params.namespace as string
        const flowId = route.params.flowId as string

        if (!ns || !flowId) {
            return {title: ""}
        }

        const breadcrumb: KsBreadcrumbItem[] = [
            ...namespaceBreadcrumb.value,
            {
                label: flowId,
                link: {
                    name: "flows/update",
                    params: {
                        namespace: ns,
                        id: flowId,
                    },
                },
            },
        ]

        const base = {
            title: route.params.id as string,
            bookmarkLabel: `${ns}.${flowId}: ${route.params.id}`,
            breadcrumb,
        }

        if (executionsStore.execution?.loopRun) {
            const loopRun = executionsStore.execution.loopRun
            let rootId = loopRun.rootExecutionId
            if (!rootId) {
                if (!loopRun.parents || loopRun.parents.length === 0) {
                    rootId = executionsStore.execution.parentId
                } else {
                    let ancestor = loopRun.parent
                    while (ancestor?.loopRun) {
                        ancestor = ancestor.loopRun.parent
                    }
                    rootId = ancestor?.id
                }
            }

            if (rootId) {
                base.breadcrumb.push({
                    label: t("root_execution"),
                    link: {
                        name: "executions/update",
                        params: {
                            namespace: ns,
                            flowId: flowId,
                            id: rootId,
                        },
                    },
                })
            }

            if (loopRun.parents && loopRun.parents.length > 0) {
                loopRun.parents.forEach(p => {
                    if (!p.executionId) return

                    base.breadcrumb.push({
                        label: formatLoopCrumbLabel(p.taskId, p.value, p.index),
                        link: {
                            name: "executions/update",
                            params: {
                                namespace: ns,
                                flowId: flowId,
                                id: p.executionId,
                            },
                        },
                    })
                })
            }

            if (loopRun.taskId) {
                base.breadcrumb.push({
                    label: formatLoopCrumbLabel(loopRun.taskId, loopRun.value, loopRun.index),
                    link: {
                        name: "executions/update",
                        params: {
                            namespace: ns,
                            flowId: flowId,
                            id: executionsStore.execution.id,
                        },
                    },
                })
            }
        }

        return base
    })

    const routeName = computed(() => route.params && route.params.id ? EXECUTION_PARENT_ROUTE : "")

    const ready = computed(() => {
        return executionsStore.execution !== undefined
    })

    // By the time either cleanup below runs, router navigation has already updated `route` to the
    // destination: if the store holds the flow being navigated to (e.g. breadcrumb -> flow edit,
    // #10722), clearing it here would erase data the destination page already loaded and rendered.
    const flowMatchesTarget = () => flowStore.flow?.namespace === route.params.namespace && flowStore.flow?.id === route.params.id

    const follow = () => {
        previousExecutionId.value = route.params.id as string
        executionsStore.followExecution(route.params as any, t)
    }

    // The bar is derived from the canonical tab/route definitions (executionTabs.ts):
    // the component, props and section flags live on each child route and are resolved
    // by `<router-view>`; here we only build the bar metadata from their `meta`.
    const getBaseTabs = () => {
        const namespace = executionsStore.execution?.namespace
        return EXECUTION_TAB_ROUTES.filter((tabRoute) => isExecutionTabEnabled(tabRoute.meta?.tab as string, {namespace})).map((tabRoute) => {
            const meta = tabRoute.meta ?? {}
            const name = meta.tab as string
            return {
                name,
                title: t(meta.title as string),
                locked: meta.locked as boolean | undefined,
                // Dependencies surfaces a live count and is disabled when there are none.
                ...(name === "dependencies"
                    ? {
                        count: (dependenciesCount.value ?? 0) > 0 ? dependenciesCount.value : undefined,
                        disabled: !dependenciesCount.value,
                    }
                    : {}),
            }
        })
    }

    const tabs = computed(() => getBaseTabs())

    const setupLifecycle = () => {
        onMounted(async () => {
            // The default-tab redirect now lives on the parent route record (routes.ts).
            follow()
            window.addEventListener("popstate", follow)

            dependenciesCount.value = (await flowStore.loadDependencies({namespace: route.params.namespace as string, id: route.params.flowId as string, subtype: "FLOW"}, true)).count
            previousExecutionId.value = route.params.id as string
        })

        watch(route, () => {
            if (previousExecutionId.value !== route.params.id) {
                executionsStore.resetLogs()
                if (!flowMatchesTarget()) {
                    flowStore.flow = undefined
                    flowStore.flowGraph = undefined
                    flowStore.invalidGraph = false
                }
                follow()
            }
        })

        onUnmounted(() => {
            executionsStore.closeSSE()
            window.removeEventListener("popstate", follow)
            executionsStore.execution = undefined
            executionsStore.resetLogs()
            if (!flowMatchesTarget()) {
                flowStore.flow = undefined
                flowStore.flowGraph = undefined
                flowStore.invalidGraph = false
            }
        })
    }

    return {
        tabs,
        ready,
        routeInfo,
        routeName,
        dependenciesCount,
        previousExecutionId,
        follow,
        getBaseTabs,
        setupLifecycle,
    }
}
