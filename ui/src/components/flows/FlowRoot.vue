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

<script>
    import FlowRevisions from "./FlowRevisions.vue"
    import LogsWrapper from "../logs/LogsWrapper.vue"
    import FlowExecutions from "./FlowExecutions.vue"
    import RouteContext from "../../mixins/routeContext"
    import {mapStores} from "pinia"
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

    export default {
        mixins: [RouteContext],
        components: {
            FlowRootTopBar,
        },
        data() {
            return {
                previousFlow: undefined,
                dependenciesCount: undefined,
                deleted: false,
                tabsOwnerId: Symbol("flow-root-tabs"),
            }
        },
        watch: {
            tabs: {
                immediate: true,
                deep: true,
                handler() {
                    this.syncTabsToStore()
                },
            },
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name) {
                    this.load()
                }
            },
            "$route.params.tab": {
                immediate: true,
                handler: function (newTab) {
                    if (newTab === "overview" || newTab === "executions") {
                        const dateTimeKeys = ["startDate", "endDate", "timeRange"]

                        if (!Object.keys(this.$route.query).some((key) => dateTimeKeys.some((dateTimeKey) => key.includes(dateTimeKey)))) {
                            const DEFAULT_DURATION = this.miscStore.configs?.chartDefaultDuration ?? "PT24H"
                            const newQuery = {...this.$route.query, "filters[timeRange][EQUALS]": DEFAULT_DURATION}
                            this.$router.replace({name: this.$route.name, params: this.$route.params, query: newQuery})
                        }
                    }
                },
            },
            "flowStore.flow": {
                deep: true,
                handler: function (flow) {
                    if (flow && flow.id) {
                        // https://github.com/kestra-io/kestra/issues/10484
                        setTimeout(() => {
                            this.flowStore
                                .loadDependencies({namespace: flow.namespace, id: flow.id}, true)
                                .then(({count}) => this.dependenciesCount = count > 0 ? (count - 1) : 0)
                        }, 1000)
                    }
                },
            },
        },
        created() {
            if(!this.$route.params.tab) {
                const tab = localStorage.getItem("flowDefaultTab") || "overview"
                this.$router.replace({
                    name: "flows/update",
                    params: {...this.$route.params, tab},
                    query: {...this.$route.query},
                })
            }
            // since this component is only used in edition
            // we need to set the flag as editing in the store.
            // Specifically, it would be a problem when saving a new flow
            // and moving to edit mode.
            // NOTE: Flow creation component is ./FlowCreate.vue
            this.flowStore.isCreating = false

            this.load()
        },
        methods: {
            load() {
                if (
                    this.flowStore.flow === undefined ||
                    this.previousFlow !== this.flowKey()
                ) {
                    const query = {...this.$route.query, allowDeleted: true}
                    return this.flowStore.loadFlow({
                        ...this.$route.params,
                        ...query,
                    })
                        .then(() => {
                            if (this.flowStore.flow) {
                                this.deleted = this.flowStore.flow.deleted
                                this.previousFlow = this.flowKey()
                                this.flowStore.loadGraph({
                                    flow: this.flowStore.flow,
                                })
                            }
                        })
                }
            },
            flowKey() {
                return this.$route.params.namespace + "/" + this.$route.params.id
            },
            getTabs() {
                let tabs = []

                if (this.user?.hasAny(resource.EXECUTION)) {
                    tabs.push({
                        name: "overview",
                        component: Overview,
                        title: this.$t("overview"),
                    })
                }

                if (
                    this.user &&
                    this.flowStore.flow &&
                    this.user.isAllowed(
                        resource.EXECUTION,
                        action.VIEW,
                        this.flowStore.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "executions",
                        component: FlowExecutions,
                        title: this.$t("executions"),
                    })
                }

                if (
                    this.user &&
                    this.flowStore.flow &&
                    this.user.isAllowed(
                        resource.FLOW,
                        action.VIEW,
                        this.flowStore.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "edit",
                        component: MultiPanelFlowEditorView,
                        title: this.$t("edit"),
                        maximized: true,
                    })
                }

                if (
                    this.user &&
                    this.flowStore.flow &&
                    this.user.isAllowed(
                        resource.FLOW,
                        action.VIEW,
                        this.flowStore.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "revisions",
                        component: FlowRevisions,
                        title: this.$t("revisions"),
                    })
                }

                if (
                    this.user &&
                    this.flowStore.flow &&
                    this.user.isAllowed(
                        resource.FLOW,
                        action.VIEW,
                        this.flowStore.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "triggers",
                        component: FlowTriggers,
                        title: this.$t("triggers"),
                    })
                }

                if (
                    this.user &&
                    this.flowStore.flow &&
                    this.user.isAllowed(
                        resource.EXECUTION,
                        action.VIEW,
                        this.flowStore.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "logs",
                        component: LogsWrapper,
                        title: this.$t("logs"),
                        props: {
                            showFilters: true,
                            restoreurl: false,
                        },
                    })
                }

                if (
                    this.user &&
                    this.flowStore.flow &&
                    this.user.isAllowed(
                        resource.EXECUTION,
                        action.VIEW,
                        this.flowStore.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "metrics",
                        component: FlowMetrics,
                        title: this.$t("metrics"),
                    })
                }
                if (
                    this.user &&
                    this.flowStore.flow &&
                    this.user.isAllowed(
                        resource.FLOW,
                        action.VIEW,
                        this.flowStore.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "dependencies",
                        component: Dependencies,
                        title: this.$t("dependencies"),
                        count: (this.dependenciesCount ?? 0) > 0 ? this.dependenciesCount : undefined,
                        disabled: !this.dependenciesCount,
                        maximized: true,
                    })
                }

                tabs.push({
                    name: "concurrency",
                    title: this.$t("concurrency"),
                    component: FlowConcurrency,
                })

                tabs.push({
                    name: "auditlogs",
                    title: this.$t("auditlogs"),
                    component: DemoAuditLogs,
                    props: {
                        embed: true,
                    },
                    locked: true,
                })

                return tabs
            },
            updateExpandedSubflows(expandedSubflows) {
                this.flowStore.expandedSubflows = expandedSubflows
            },
            syncTabsToStore() {
                this.routeTabsStore.setTabs({
                    ownerId: this.tabsOwnerId,
                    tabs: this.tabs,
                    routeName: "flows/update",
                    displayMode: "select",
                })
            },
        },
        computed: {
            ...mapStores(useFlowStore, useAuthStore, useMiscStore, useRouteTabsStore),
            activeTab() {
                const key = this.$route?.params?.tab
                return this.tabs.find(t => t.name === key) ?? this.tabs[0]
            },
            activeTabName() {
                return this.activeTab?.name ?? "home"
            },
            containerClass() {
                if (this.activeTab?.locked) return {"px-0": true, "full-container": true}
                return {"container": true, "tabs-flush-top": true}
            },
            routeInfo() {
                return {
                    title: this.$route.params.id,
                    breadcrumb: [
                        {
                            label: this.$t("flows"),
                            link: {
                                name: "flows/list",
                            },
                        },
                        {
                            label: this.$route.params.namespace,
                            link: {
                                name: "namespaces/update",
                                params: {
                                    id: this.$route.params.namespace,
                                    tab: "flows",
                                },
                            },
                        },
                    ],
                    beta: this.tabs.find(tab => tab.name === this.$route.params.tab)?.props?.beta,
                }
            },
            tabs() {
                return this.getTabs()
            },
            ready() {
                return this.user && this.flowStore.flow
            },
            user() {
                return this.authStore.user
            },
        },
        beforeUnmount() {
            this.routeTabsStore.clearTabsIfOwner(this.tabsOwnerId)
        },
        unmounted() {
            this.flowStore.flow = undefined
            this.flowStore.flowGraph = undefined
        },
    }
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
