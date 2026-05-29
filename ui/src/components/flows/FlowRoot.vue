<template>
    <template v-if="ready">
        <FlowRootTopBar
            :routeInfo="routeInfo"
            :activeTabName="activeTabName"
        />
        <section
            v-if="activeTab"
            :class="[containerClass, {maximized: activeTab.maximized, 'no-overflow': activeTab.noOverflow}]"
        >
            <component
                :is="activeTab.component"
                v-bind="activeTab.props"
                :embed="activeTab.props?.embed ?? true"
                @expand-subflow="updateExpandedSubflows"
            />
        </section>
    </template>
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount, onMounted, onUnmounted, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"

    import FlowRevisions from "./FlowRevisions.vue"
    import LogsWrapper from "../logs/LogsWrapper.vue"
    import FlowExecutions from "./FlowExecutions.vue"
    import {useFlowStore} from "../../stores/flow"
    import {useRouteTabsStore} from "../../stores/routeTabs"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import Overview from "./Overview.vue"
    import Dependencies from "../dependencies/Dependencies.vue"
    import FlowMetrics from "./FlowMetrics.vue"
    import MultiPanelFlowEditorView from "./MultiPanelFlowEditorView.vue"
    import FlowTriggers from "./FlowTriggers.vue"
    import FlowRootTopBar from "./FlowRootTopBar.vue"
    import FlowConcurrency from "./FlowConcurrency.vue"
    import DemoAuditLogs from "../demo/AuditLogs.vue"
    import {useAuthStore} from "override/stores/auth"
    import {useMiscStore} from "override/stores/misc"
    import useRouteContext from "../../composables/useRouteContext"

    const {t} = useI18n({useScope: "global"})
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
    const ready = computed(() => user.value && flowStore.flow)

    const tabs = computed(() => {
        const result = []

        if (user.value?.hasAny(resource.EXECUTION)) {
            result.push({
                name: "overview",
                component: Overview,
                title: t("overview"),
            })
        }

        if (user.value && flowStore.flow && user.value.isAllowed(resource.EXECUTION, action.VIEW, flowStore.flow.namespace)) {
            result.push({
                name: "executions",
                component: FlowExecutions,
                title: t("executions"),
            })
        }

        if (user.value && flowStore.flow && user.value.isAllowed(resource.FLOW, action.VIEW, flowStore.flow.namespace)) {
            result.push({
                name: "edit",
                component: MultiPanelFlowEditorView,
                title: t("edit"),
                maximized: true,
            })
        }

        if (user.value && flowStore.flow && user.value.isAllowed(resource.FLOW, action.VIEW, flowStore.flow.namespace)) {
            result.push({
                name: "revisions",
                component: FlowRevisions,
                title: t("revisions"),
            })
        }

        if (user.value && flowStore.flow && user.value.isAllowed(resource.FLOW, action.VIEW, flowStore.flow.namespace)) {
            result.push({
                name: "triggers",
                component: FlowTriggers,
                title: t("triggers"),
            })
        }

        if (user.value && flowStore.flow && user.value.isAllowed(resource.EXECUTION, action.VIEW, flowStore.flow.namespace)) {
            result.push({
                name: "logs",
                component: LogsWrapper,
                title: t("logs"),
                props: {
                    showFilters: true,
                    restoreurl: false,
                },
            })
        }

        if (user.value && flowStore.flow && user.value.isAllowed(resource.EXECUTION, action.VIEW, flowStore.flow.namespace)) {
            result.push({
                name: "metrics",
                component: FlowMetrics,
                title: t("metrics"),
            })
        }

        if (user.value && flowStore.flow && user.value.isAllowed(resource.FLOW, action.VIEW, flowStore.flow.namespace)) {
            result.push({
                name: "dependencies",
                component: Dependencies,
                title: t("dependencies"),
                count: (dependenciesCount.value ?? 0) > 0 ? dependenciesCount.value : undefined,
                disabled: !dependenciesCount.value,
                maximized: true,
            })
        }

        result.push({
            name: "concurrency",
            title: t("concurrency"),
            component: FlowConcurrency,
        })

        result.push({
            name: "auditlogs",
            title: t("auditlogs"),
            component: DemoAuditLogs,
            props: {embed: true},
            locked: true,
        })

        return result
    })

    const activeTab = computed(() => {
        const key = route?.params?.tab
        return tabs.value.find(t => t.name === key) ?? tabs.value[0]
    })

    const activeTabName = computed(() => activeTab.value?.name ?? "home")

    const containerClass = computed(() => {
        if (activeTab.value?.locked) return {"px-0": true, "full-container": true}
        return {"container": true, "tabs-flush-top": true}
    })

    const routeInfo = computed(() => ({
        title: route.params.id as string,
        breadcrumb: [
            {
                label: t("flows"),
                link: {name: "flows/list"},
            },
            {
                label: route.params.namespace as string,
                link: {
                    name: "namespaces/update",
                    params: {id: route.params.namespace, tab: "flows"},
                },
            },
        ],
        beta: tabs.value.find(tab => tab.name === route.params.tab)?.props?.beta,
    }))

    useRouteContext(routeInfo)

    function flowKey() {
        return route.params.namespace + "/" + route.params.id
    }

    function load() {
        if (flowStore.flow === undefined || previousFlow.value !== flowKey()) {
            const query = {...route.query, allowDeleted: true}
            return flowStore.loadFlow({...route.params, ...query})
                .then(() => {
                    if (flowStore.flow) {
                        deleted.value = flowStore.flow.deleted
                        previousFlow.value = flowKey()
                        flowStore.loadGraph({flow: flowStore.flow})
                    }
                })
        }
    }

    function syncTabsToStore() {
        routeTabsStore.setTabs({
            ownerId: tabsOwnerId,
            tabs: tabs.value,
            routeName: "flows/update",
            displayMode: "select",
        })
    }

    function updateExpandedSubflows(expandedSubflows: unknown) {
        flowStore.expandedSubflows = expandedSubflows
    }

    watch(tabs, () => {
        syncTabsToStore()
    }, {immediate: true, deep: true})

    watch(route, (newValue, oldValue) => {
        if (oldValue.name === newValue.name) {
            load()
        }
    })

    watch(() => route.params.tab, (newTab) => {
        if (newTab === "overview" || newTab === "executions") {
            const dateTimeKeys = ["startDate", "endDate", "timeRange"]
            if (!Object.keys(route.query).some((key) => dateTimeKeys.some((dateTimeKey) => key.includes(dateTimeKey)))) {
                const DEFAULT_DURATION = miscStore.configs?.chartDefaultDuration ?? "PT24H"
                const newQuery = {...route.query, "filters[timeRange][EQUALS]": DEFAULT_DURATION}
                router.replace({name: route.name, params: route.params, query: newQuery})
            }
        }
    }, {immediate: true})

    watch(() => flowStore.flow, (flow) => {
        if (flow && flow.id) {
            // https://github.com/kestra-io/kestra/issues/10484
            setTimeout(() => {
                flowStore.loadDependencies({namespace: flow.namespace, id: flow.id}, true)
                    .then(({count}: {count: number}) => dependenciesCount.value = count > 0 ? (count - 1) : 0)
            }, 1000)
        }
    }, {deep: true})

    onMounted(() => {
        if (!route.params.tab) {
            const tab = localStorage.getItem("flowDefaultTab") || "overview"
            router.replace({
                name: "flows/update",
                params: {...route.params, tab},
                query: {...route.query},
            })
        }
        // since this component is only used in edition
        // we need to set the flag as editing in the store.
        // Specifically, it would be a problem when saving a new flow
        // and moving to edit mode.
        // NOTE: Flow creation component is ./FlowCreate.vue
        flowStore.isCreating = false

        load()
    })

    onBeforeUnmount(() => {
        routeTabsStore.clearTabsIfOwner(tabsOwnerId)
    })

    onUnmounted(() => {
        flowStore.flow = undefined
        flowStore.flowGraph = undefined
    })
</script>
<style scoped lang="scss">
    .gray-700 {
        color: var(--ks-text-secondary-color);
    }
    .body-color {
        color: var(--ks-text-primary);
    }

    section.maximized {
        margin: 0 !important;
        padding: 0;
        flex-grow: 1;
    }

    section.no-overflow {
        overflow: hidden;
    }
</style>
