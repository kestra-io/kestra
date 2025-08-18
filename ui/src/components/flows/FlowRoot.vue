<template>
    <template v-if="ready">
        <FlowRootTopBar
            :route-info="routeInfo"
            :active-tab-name="activeTabName()"
        />
        <Tabs
            route-name="flows/update"
            ref="currentTab"
            :tabs="tabs"
            @expand-subflow="updateExpandedSubflows"
        />
    </template>
</template>

<script>
    import Topology from "./Topology.vue";
    import FlowRevisions from "./FlowRevisions.vue";
    import LogsWrapper from "../logs/LogsWrapper.vue"
    import FlowExecutions from "./FlowExecutions.vue";
    import RouteContext from "../../mixins/routeContext";
    import {mapStores} from "pinia";
    import {useCoreStore} from "../../stores/core";
    import {useFlowStore} from "../../stores/flow";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import Tabs from "../Tabs.vue";
    import Overview from "./Overview.vue";
    import Dependencies from "../dependencies/Dependencies.vue";
    import FlowMetrics from "./FlowMetrics.vue";
    import FlowEditor from "./FlowEditor.vue";
    import FlowTriggers from "./FlowTriggers.vue";
    import FlowRootTopBar from "./FlowRootTopBar.vue";
    import FlowConcurrency from "./FlowConcurrency.vue";
    import DemoAuditLogs from "../demo/AuditLogs.vue";
    import {useAuthStore} from "override/stores/auth"

    export default {
        mixins: [RouteContext],
        components: {
            Tabs,
            FlowRootTopBar,
        },
        data() {
            return {
                tabIndex: undefined,
                previousFlow: undefined,
                dependenciesCount: undefined,
                deleted: false,
            };
        },
        watch: {
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name) {
                    this.load();
                }
            },
            "coreStore.guidedProperties": {
                deep: true,
                immediate: true,
                handler: function (newValue) {
                    if (newValue?.manuallyContinue) {
                        setTimeout(() => {
                            this.$tours["guidedTour"]?.nextStep();
                            this.coreStore.guidedProperties = {...this.coreStore.guidedProperties, manuallyContinue: false};
                        }, 500);
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
                                .loadDependencies({namespace: flow.namespace, id: flow.id})
                                .then(({count}) => this.dependenciesCount = count);
                        }, 1000);
                    }
                },
            }
        },
        created() {
            if(!this.$route.params.tab) {
                const tab = localStorage.getItem("flowDefaultTab") || undefined;
                this.$router.replace({name: "flows/update", params: {...this.$route.params, tab}});
            }
            // since this component is only used in edition
            // we need to set the flag as editing in the store.
            // Specifically, it would be a problem when saving a new flow
            // and moving to edit mode.
            // NOTE: Flow creation component is ./FlowCreate.vue
            this.flowStore.isCreating = false;

            this.load();
        },
        methods: {
            load() {
                if (
                    this.flowStore.flow === undefined ||
                    this.previousFlow !== this.flowKey()
                ) {
                    const query = {...this.$route.query, allowDeleted: true};
                    return this.flowStore.loadFlow({
                        ...this.$route.params,
                        ...query,
                    })
                        .then(() => {
                            if (this.flowStore.flow) {
                                this.deleted = this.flowStore.flow.deleted;
                                this.previousFlow = this.flowKey();
                                this.flowStore.loadGraph({
                                    flow: this.flowStore.flow,
                                });

                                return this.flowStore.loadDependencies({
                                    namespace: this.$route.params.namespace,
                                    id: this.$route.params.id
                                });
                            }
                        }).then(({count}) => {
                            this.dependenciesCount = count;
                        });
                }
            },
            flowKey() {
                return this.$route.params.namespace + "/" + this.$route.params.id;
            },
            getTabs() {
                let tabs = [
                    {
                        name: undefined,
                        component: Topology,
                        title: this.$t("topology"),
                        props: {
                            isReadOnly: true,
                            expandedSubflows: this.flowStore.expandedSubflows,
                        },
                    },
                ];

                if (this.user.hasAny(permission.EXECUTION)) {
                    tabs[0].name = "topology";

                    tabs = [
                        {
                            name: undefined,
                            component: Overview,
                            title: this.$t("overview"),
                            containerClass: "full-container flex-grow-0 flex-shrink-0 flex-basis-0",
                        },
                    ].concat(tabs);
                }

                if (
                    this.user &&
                    this.flowStore.flow &&
                    this.user.isAllowed(
                        permission.EXECUTION,
                        action.READ,
                        this.flowStore.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "executions",
                        component: FlowExecutions,
                        title: this.$t("executions"),
                    });
                }

                if (
                    this.user &&
                    this.flowStore.flow &&
                    this.user.isAllowed(
                        permission.FLOW,
                        action.READ,
                        this.flowStore.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "edit",
                        component: FlowEditor,
                        title: this.$t("edit"),
                        containerClass: "full-container",
                        maximized: true,
                        props: {
                            expandedSubflows: this.flowStore.expandedSubflows,
                            isReadOnly: this.deleted || !this.flowStore.isAllowedEdit || this.flowStore.readOnlySystemLabel,
                        },
                    });
                }

                if (
                    this.user &&
                    this.flowStore.flow &&
                    this.user.isAllowed(
                        permission.FLOW,
                        action.READ,
                        this.flowStore.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "revisions",
                        component: FlowRevisions,
                        containerClass: "container full-height",
                        title: this.$t("revisions"),
                    });
                }

                if (
                    this.user &&
                    this.flowStore.flow &&
                    this.user.isAllowed(
                        permission.FLOW,
                        action.READ,
                        this.flowStore.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "triggers",
                        component: FlowTriggers,
                        title: this.$t("triggers"),
                    });
                }

                if (
                    this.user &&
                    this.flowStore.flow &&
                    this.user.isAllowed(
                        permission.EXECUTION,
                        action.READ,
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
                        containerClass: "container"
                    });
                }

                if (
                    this.user &&
                    this.flowStore.flow &&
                    this.user.isAllowed(
                        permission.EXECUTION,
                        action.READ,
                        this.flowStore.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "metrics",
                        component: FlowMetrics,
                        title: this.$t("metrics"),
                    });
                }
                if (
                    this.user &&
                    this.flowStore.flow &&
                    this.user.isAllowed(
                        permission.FLOW,
                        action.READ,
                        this.flowStore.flow.namespace,
                    )
                ) {
                    tabs.push({
                        name: "dependencies",
                        component: Dependencies,
                        title: this.$t("dependencies"),
                        count: this.dependenciesCount,
                        maximized: true
                    });
                }

                tabs.push({
                    name: "concurrency",
                    title: this.$t("concurrency"),
                    component: FlowConcurrency
                })

                tabs.push(                    {
                    name: "auditlogs",
                    title: this.$t("auditlogs"),
                    component: DemoAuditLogs,
                    maximize: true,
                    props:{
                        embed: true
                    },
                    locked: true
                });

                return tabs;
            },
            updateExpandedSubflows(expandedSubflows) {
                this.flowStore.expandedSubflows = expandedSubflows;
            },
            activeTabName() {
                return this.$refs.currentTab?.activeTab?.name ?? "home";
            }
        },
        computed: {
            ...mapStores(useCoreStore, useFlowStore, useAuthStore),
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
                                    tab: "flows"
                                }
                            }
                        },
                    ],
                    beta: this.tabs.find(tab => tab.name === this.$route.params.tab)?.props?.beta,
                };
            },
            tabs() {
                return this.getTabs();
            },
            ready() {
                return this.user && this.flowStore.flow;
            },
            user() {
                return this.authStore.user;
            }
        },
        unmounted() {
            this.flowStore.flow = undefined;
            this.flowStore.flowGraph = undefined;
        },
    };
</script>
<style lang="scss" scoped>
.gray-700 {
    color: var(--ks-content-secondary-color);
}
.body-color {
    color: var(--ks-content-primary);
}
</style>