import {ref, computed, onMounted, onUnmounted, watch} from "vue"
import {useRoute} from "vue-router"
import {useI18n} from "vue-i18n"

import {useFlowStore} from "../../../stores/flow"
import {useExecutionsStore} from "../../../stores/executions"

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

    const routeName = computed(() => route.params && route.params.id ? "executions/update" : "")

    const ready = computed(() => {
        return executionsStore.execution !== undefined
    })

    const follow = () => {
        previousExecutionId.value = route.params.id as string
        executionsStore.followExecution(route.params as any, t)
    }

    // Bar metadata only: the rendered component, its props and section flags now live
    // on the matching child route (see routes.ts) and are resolved by `<router-view>`.
    const getBaseTabs = () => {
        return [
            {
                name: "overview",
                title: t("overview"),
            },
            {
                name: "gantt",
                title: t("gantt"),
            },
            {
                name: "logs",
                title: t("logs"),
            },
            {
                name: "outputs",
                title: t("variable_explorer.title"),
            },
            {
                name: "metrics",
                title: t("metrics"),
            },
            {
                name: "dependencies",
                title: t("dependencies"),
                count: (dependenciesCount.value ?? 0) > 0 ? dependenciesCount.value : undefined,
                disabled: !dependenciesCount.value,
            },
            {
                name: "auditlogs",
                title: t("auditlogs"),
                locked: true,
            },
            {
                name: "assets",
                title: t("assets.title"),
                locked: true,
            },
        ]
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
