import {ref, computed, onMounted, onUnmounted, watch} from "vue"
import {useRoute} from "vue-router"
import {useI18n} from "vue-i18n"

import {useFlowStore} from "../../../stores/flow"
import {useExecutionsStore} from "../../../stores/executions"
import {EXECUTION_PARENT_ROUTE, EXECUTION_TAB_ROUTES} from "../executionTabs"

export function useExecutionRoot() {
    const {t} = useI18n()
    const route = useRoute()

    const flowStore = useFlowStore()
    const executionsStore = useExecutionsStore()

    const dependenciesCount = ref<number>()
    const previousExecutionId = ref<string>()

    const routeInfo = computed(() => {
        const ns = route.params.namespace as string
        const flowId = route.params.flowId as string

        if (!ns || !flowId) {
            return {title: ""}
        }

        return {
            title: route.params.id as string,
            breadcrumb: [
                {
                    label: t("executions"),
                    link: {
                        name: "executions/list",
                    },
                },
                {
                    label: `${ns}.${flowId}`,
                    link: {
                        name: "flows/update",
                        params: {
                            namespace: ns,
                            id: flowId,
                        },
                    },
                },
            ],
        }
    })

    const routeName = computed(() => route.params && route.params.id ? EXECUTION_PARENT_ROUTE : "")

    const ready = computed(() => {
        return executionsStore.execution !== undefined
    })

    const follow = () => {
        previousExecutionId.value = route.params.id as string
        executionsStore.followExecution(route.params as any, t)
    }

    // The bar is derived from the canonical tab/route definitions (executionTabs.ts):
    // the component, props and section flags live on each child route and are resolved
    // by `<router-view>`; here we only build the bar metadata from their `meta`.
    const getBaseTabs = () => {
        return EXECUTION_TAB_ROUTES.map((tabRoute) => {
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
                executionsStore.logs = {total: 0, results: []}
                flowStore.flow = undefined
                flowStore.flowGraph = undefined
                follow()
            }
        })

        onUnmounted(() => {
            executionsStore.closeSSE()
            window.removeEventListener("popstate", follow)
            executionsStore.execution = undefined
            executionsStore.logs = {total: 0, results: []}
            flowStore.flow = undefined
            flowStore.flowGraph = undefined
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
