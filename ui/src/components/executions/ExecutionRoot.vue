<template>
    <template v-if="ready">
        <execution-root-top-bar :route-info="routeInfo" />
        <tabs
            :route-name="$route.params && $route.params.id ? 'executions/update': ''"
            @follow="follow"
            :tabs="tabs"
        />
    </template>
    <div v-else class="full-space" v-loading="true">
        {{ executionsStore.execution?.id }}
    </div>
</template>

<script>
    import {mapState} from "vuex";
    import {mapStores} from "pinia";

    import Gantt from "./Gantt.vue";
    import Overview from "./Overview.vue";
    import Logs from "./Logs.vue";
    import Topology from "./Topology.vue";
    import ExecutionOutput from "./outputs/Wrapper.vue";
    import ExecutionMetric from "./ExecutionMetric.vue";
    import RouteContext from "../../mixins/routeContext";
    import {useCoreStore} from "../../stores/core";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import Tabs from "../../components/Tabs.vue";
    import ExecutionRootTopBar from "./ExecutionRootTopBar.vue";
    import DemoAuditLogs from "../demo/AuditLogs.vue";

    import ExecutionDependencies from "./ExecutionDependencies.vue";

    import {useExecutionsStore} from "../../stores/executions";

    export default {
        mixins: [RouteContext],
        components: {
            Tabs,
            ExecutionRootTopBar,
        },
        data() {
            return {
                sse: undefined,
                previousExecutionId: undefined,
            };
        },
        created() {
            if(!this.$route.params.tab) {
                const tab = localStorage.getItem("executeDefaultTab") || undefined;
                this.$router.replace({name: "executions/update", params: {...this.$route.params, tab}});
            }

            this.follow();
            window.addEventListener("popstate", this.follow)
        },
        mounted() {
            this.previousExecutionId = this.$route.params.id
        },
        watch: {
            $route() {
                this.executionsStore.taskRun = undefined;
                if (this.previousExecutionId !== this.$route.params.id) {
                    this.$store.commit("flow/setFlow", undefined);
                    this.$store.commit("flow/setFlowGraph", undefined);
                    this.follow();
                }
            },
        },
        methods: {
            follow() {
                this.previousExecutionId = this.$route.params.id;
                this.executionsStore.followExecution(this.$route.params, this.$t);
            },
            getTabs() {
                return [
                    {
                        name: undefined,
                        component: Overview,
                        title: this.$t("overview"),
                    },
                    {
                        name: "gantt",
                        component: Gantt,
                        title: this.$t("gantt")
                    },
                    {
                        name: "logs",
                        component: Logs,
                        title: this.$t("logs")
                    },
                    {
                        name: "topology",
                        component: Topology,
                        title: this.$t("topology")
                    },
                    {
                        name: "outputs",
                        component: ExecutionOutput,
                        title: this.$t("outputs"),
                        maximized: true
                    },
                    {
                        name: "metrics",
                        component: ExecutionMetric,
                        title: this.$t("metrics")
                    },
                    {
                        name: "dependencies",
                        component: ExecutionDependencies,
                        title: this.$t("dependencies"),
                        props: {
                            isReadOnly: true,
                        },
                    },
                    {
                        name: "auditlogs",
                        component: DemoAuditLogs,
                        title: this.$t("auditlogs"),
                        maximized: true,
                        locked: true
                    }
                ];
            }
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapStores(useCoreStore, useExecutionsStore),
            tabs() {
                return this.getTabs();
            },
            routeInfo() {
                const ns = this.$route.params.namespace;
                const flowId = this.$route.params.flowId;

                if (!ns || !flowId) {
                    return {};
                }

                return {
                    title: this.$route.params.id,
                    breadcrumb: [
                        {
                            label: this.$t("flows"),
                            link: {
                                name: "flows/list",
                                query: {
                                    namespace: ns
                                }
                            }
                        },
                        {
                            label: `${ns}.${flowId}`,
                            link: {
                                name: "namespaces/update",
                                params: {
                                    id: ns,
                                    tab: "executions"
                                }
                            }
                        },
                        {
                            label: this.$t("executions"),
                            link: {
                                name: "flows/update",
                                params: {
                                    namespace: ns,
                                    id: flowId,
                                    tab: "executions"
                                }
                            }
                        }
                    ]
                };
            },
            isAllowedTrigger() {
                return this.user
                    && this.executionsStore.execution
                    && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.executionsStore.execution.namespace);
            },
            isAllowedEdit() {
                return this.user
                    && this.executionsStore.execution
                    && this.user.isAllowed(permission.FLOW, action.UPDATE, this.executionsStore.execution.namespace);
            },
            canDelete() {
                return this.user
                    && this.executionsStore.execution
                    && this.user.isAllowed(permission.EXECUTION, action.DELETE, this.executionsStore.execution.namespace);
            },
            ready() {
                return this.executionsStore.execution !== undefined;
            }
        },
        beforeUnmount() {
            this.executionsStore.closeSSE();
            window.removeEventListener("popstate", this.follow)
            this.executionsStore.execution = undefined;
            this.$store.commit("flow/setFlow", undefined);
            this.$store.commit("flow/setFlowGraph", undefined);
        }
    };
</script>
<style lang="scss" scoped>
    .full-space {
        flex: 1 1 auto;
    }
</style>
