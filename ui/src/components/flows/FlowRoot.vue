<template>
    <template v-if="ready">
        <FlowRootTopBar
            :routeInfo="routeInfo"
            :activeTabName="activeTabName()"
        />
        <Tabs
            routeName="flows/update"
            ref="currentTab"
            :tabs="(tabs as any)"
            @expand-subflow="updateExpandedSubflows"
        />
    </template>
</template>

<script setup lang="ts">
    import {ref, computed, watch, onUnmounted, useTemplateRef} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import useRouteContext from "../../composables/useRouteContext"
    import Topology from "./Topology.vue"
    import FlowRevisions from "./FlowRevisions.vue"
    import LogsWrapper from "../logs/LogsWrapper.vue"
    import FlowExecutions from "./FlowExecutions.vue"
    import {useFlowStore} from "../../stores/flow"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import Tabs from "../Tabs.vue"
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

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()
    const router = useRouter()
    const flowStore = useFlowStore()
    const authStore = useAuthStore()
    const miscStore = useMiscStore()

    const currentTab = useTemplateRef<InstanceType<typeof Tabs>>("currentTab")

    const previousFlow = ref<string | undefined>(undefined)
    const dependenciesCount = ref<number | undefined>(undefined)
    const deleted = ref(false)

    const user = computed(() => authStore.user)

    const tabs = computed(() => getTabs())

    const routeInfo = computed(() => ({
        title: route.params.id as string,
        breadcrumb: [
            {
                label: t("flows"),
                link: {
                    name: "flows/list",
                },
            },
            {
                label: route.params.namespace as string,
                link: {
                    name: "namespaces/update",
                    params: {
                        id: route.params.namespace,
                        tab: "flows",
                    },
                },
            },
        ],
        beta: (tabs.value.find(tab => (tab as any).name === route.params.tab) as any)?.props?.beta,
    }))

    const ready = computed(() => user.value && flowStore.flow)

    useRouteContext(routeInfo)

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
                router.replace({name: route.name!, params: route.params, query: newQuery})
            }
        }
    }, {immediate: true})

    watch(() => flowStore.flow, (flow) => {
        if (flow && flow.id) {
            // https://github.com/kestra-io/kestra/issues/10484
            setTimeout(() => {
                flowStore
                    .loadDependencies({namespace: flow.namespace, id: flow.id, subtype: "FLOW" as const}, true)
                    .then(({count}: { count: number }) => dependenciesCount.value = count > 0 ? (count - 1) : 0)
            }, 1000)
        }
    }, {deep: true})

    // since this component is only used in edition
    // we need to set the flag as editing in the store.
    // Specifically, it would be a problem when saving a new flow
    // and moving to edit mode.
    // NOTE: Flow creation component is ./FlowCreate.vue
    flowStore.isCreating = false

    if (!route.params.tab) {
        const tab = localStorage.getItem("flowDefaultTab") || "overview"
        router.replace({
            name: "flows/update",
            params: {...route.params, tab},
            query: {...route.query},
        })
    }

    load()

    function load() {
        if (
            flowStore.flow === undefined ||
            previousFlow.value !== flowKey()
        ) {
            const query = {...route.query, allowDeleted: true}
            return flowStore.loadFlow({
                namespace: route.params.namespace as string,
                id: route.params.id as string,
                ...query,
            })
                .then(() => {
                    if (flowStore.flow) {
                        deleted.value = flowStore.flow.deleted ?? false
                        previousFlow.value = flowKey()
                        flowStore.loadGraph({
                            flow: flowStore.flow,
                        })
                    }
                })
        }
    }

    function flowKey() {
        return route.params.namespace + "/" + route.params.id
    }

    function getTabs() {
        let tabList: Array<any> = [
            {
                name: undefined,
                component: Topology,
                title: t("topology"),
                props: {
                    isReadOnly: true,
                    expandedSubflows: flowStore.expandedSubflows,
                },
            },
        ]

        if (user.value?.hasAny(resource.EXECUTION)) {
            tabList[0].name = "topology"

            tabList = [
                {
                    name: "overview",
                    component: Overview,
                    title: t("overview"),
                    containerClass: "full-container flex-grow-0 flex-shrink-0 flex-basis-0",
                },
            ].concat(tabList)
        }

        if (
            user.value &&
            flowStore.flow &&
            user.value.isAllowed(
                resource.EXECUTION,
                action.VIEW,
                flowStore.flow.namespace,
            )
        ) {
            tabList.push({
                name: "executions",
                component: FlowExecutions,
                title: t("executions"),
            })
        }

        if (
            user.value &&
            flowStore.flow &&
            user.value.isAllowed(
                resource.FLOW,
                action.VIEW,
                flowStore.flow.namespace,
            )
        ) {
            tabList.push({
                name: "edit",
                component: MultiPanelFlowEditorView,
                title: t("edit"),
                containerClass: "full-container",
                maximized: true,
            })
        }

        if (
            user.value &&
            flowStore.flow &&
            user.value.isAllowed(
                resource.FLOW,
                action.VIEW,
                flowStore.flow.namespace,
            )
        ) {
            tabList.push({
                name: "revisions",
                component: FlowRevisions,
                containerClass: "container full-height",
                title: t("revisions"),
            })
        }

        if (
            user.value &&
            flowStore.flow &&
            user.value.isAllowed(
                resource.FLOW,
                action.VIEW,
                flowStore.flow.namespace,
            )
        ) {
            tabList.push({
                name: "triggers",
                component: FlowTriggers,
                title: t("triggers"),
            })
        }

        if (
            user.value &&
            flowStore.flow &&
            user.value.isAllowed(
                resource.EXECUTION,
                action.VIEW,
                flowStore.flow.namespace,
            )
        ) {
            tabList.push({
                name: "logs",
                component: LogsWrapper,
                title: t("logs"),
                props: {
                    showFilters: true,
                    restoreurl: false,
                },
                containerClass: "container",
            })
        }

        if (
            user.value &&
            flowStore.flow &&
            user.value.isAllowed(
                resource.EXECUTION,
                action.VIEW,
                flowStore.flow.namespace,
            )
        ) {
            tabList.push({
                name: "metrics",
                component: FlowMetrics,
                title: t("metrics"),
            })
        }

        if (
            user.value &&
            flowStore.flow &&
            user.value.isAllowed(
                resource.FLOW,
                action.VIEW,
                flowStore.flow.namespace,
            )
        ) {
            tabList.push({
                name: "dependencies",
                component: Dependencies,
                title: t("dependencies"),
                count: (dependenciesCount.value ?? 0) > 0 ? dependenciesCount.value : undefined,
                disabled: !dependenciesCount.value,
                maximized: true,
            })
        }

        tabList.push({
            name: "concurrency",
            title: t("concurrency"),
            component: FlowConcurrency,
        })

        tabList.push({
            name: "auditlogs",
            title: t("auditlogs"),
            component: DemoAuditLogs,
            maximize: true,
            props: {
                embed: true,
            },
            locked: true,
        })

        return tabList
    }

    function updateExpandedSubflows(expandedSubflows: unknown) {
        flowStore.expandedSubflows = expandedSubflows as any
    }

    function activeTabName() {
        return (currentTab.value as any)?.activeTab?.name ?? "home"
    }

    onUnmounted(() => {
        flowStore.flow = undefined
        flowStore.flowGraph = undefined
    })
</script>

<style scoped lang="scss">
.gray-700 {
    color: var(--ks-content-secondary-color);
}
.body-color {
    color: var(--ks-content-primary);
}
</style>
