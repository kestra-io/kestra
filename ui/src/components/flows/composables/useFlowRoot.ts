import {computed, onBeforeUnmount, onUnmounted, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useI18n} from "vue-i18n"

import {useFlowStore} from "../../../stores/flow"
import {useRouteTabsStore} from "../../../stores/routeTabs"
import {useAuthStore} from "override/stores/auth"
import {useMiscStore} from "override/stores/misc"
import {useActiveTab} from "../../../composables/useActiveTab"
import {FLOW_PARENT_ROUTE, FLOW_TAB_ROUTES, isFlowTabAllowed} from "../flowTabs"

export function useFlowRoot() {
    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()

    const flowStore = useFlowStore()
    const authStore = useAuthStore()
    const miscStore = useMiscStore()
    const routeTabsStore = useRouteTabsStore()

    const previousFlow = ref<string | undefined>(undefined)
    const dependenciesCount = ref<number | undefined>(undefined)
    const deleted = ref(false)
    const tabsOwnerId = Symbol("flow-root-tabs")

    const user = computed(() => authStore.user)
    const activeTabName = useActiveTab()

    function flowKey(): string {
        return route.params.namespace + "/" + route.params.id
    }

    function load() {
        if (flowStore.flow === undefined || previousFlow.value !== flowKey()) {
            const query = {...route.query, allowDeleted: true}
            return flowStore.loadFlow({
                ...route.params,
                ...query,
            } as any).then(() => {
                if (flowStore.flow) {
                    deleted.value = Boolean(flowStore.flow.deleted)
                    previousFlow.value = flowKey()
                    flowStore.loadGraph({flow: flowStore.flow})
                }
            })
        }
    }

    // Bar metadata only: the rendered component, its props and section flags now live
    // on the matching child route (see flowTabs.ts) and are resolved by `<router-view>`.
    const tabs = computed(() => {
        const namespace = flowStore.flow?.namespace
        return FLOW_TAB_ROUTES
            .filter((tabRoute) => isFlowTabAllowed(tabRoute.meta?.tab as string, {user: user.value, namespace}))
            .map((tabRoute) => {
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
    })

    function syncTabsToStore() {
        routeTabsStore.setTabs({
            ownerId: tabsOwnerId,
            tabs: tabs.value,
            routeName: FLOW_PARENT_ROUTE,
            displayMode: "select",
        })
    }

    const routeName = computed(() => route.params && route.params.id ? FLOW_PARENT_ROUTE : "")

    const routeInfo = computed(() => ({
        title: route.params.id.toString(),
        breadcrumb: [
            {
                label: t("flows"),
                link: {name: "flows/list"},
            },
            {
                label: route.params.namespace,
                link: {
                    name: "namespaces/update",
                    params: {id: route.params.namespace, tab: "flows"},
                },
            },
        ],
        beta: route.meta?.beta as boolean | undefined,
    }))

    const ready = computed(() => user.value && flowStore.flow)

    watch(tabs, () => syncTabsToStore(), {immediate: true, deep: true})

    // Reload flow data when navigating to a different flow (namespace/id), regardless of
    // which tab that navigation lands on.
    watch(() => [route.params.namespace, route.params.id], () => load())

    watch(activeTabName, (newTab) => {
        if (newTab === "overview" || newTab === "executions") {
            const dateTimeKeys = ["startDate", "endDate", "timeRange"]
            if (!Object.keys(route.query).some((key) => dateTimeKeys.some((dateTimeKey) => key.includes(dateTimeKey)))) {
                const DEFAULT_DURATION = miscStore.configs?.chartDefaultDuration ?? "PT24H"
                const newQuery = {...route.query, "filters[timeRange][EQUALS]": DEFAULT_DURATION}
                router.replace({name: route.name as string, params: route.params, query: newQuery})
            }
        }
    }, {immediate: true})

    watch(() => flowStore.flow, (flow) => {
        if (flow && flow.id) {
            // https://github.com/kestra-io/kestra/issues/10484
            setTimeout(() => {
                flowStore
                    .loadDependencies({subtype: "FLOW", namespace: flow.namespace, id: flow.id}, true)
                    .then(({count}: {count: number}) => dependenciesCount.value = count > 0 ? (count - 1) : 0)
            }, 1000)
        }
    }, {deep: true})

    function setupLifecycle() {
        // since this component is only used in edition
        // we need to set the flag as editing in the store.
        // Specifically, it would be a problem when saving a new flow
        // and moving to edit mode.
        // NOTE: Flow creation component is ./FlowCreate.vue
        flowStore.isCreating = false
        load()

        onBeforeUnmount(() => {
            routeTabsStore.clearTabsIfOwner(tabsOwnerId)
        })

        onUnmounted(() => {
            flowStore.flow = undefined
            flowStore.flowGraph = undefined
        })
    }

    return {
        tabs,
        routeName,
        routeInfo,
        ready,
        dependenciesCount,
        setupLifecycle,
    }
}
