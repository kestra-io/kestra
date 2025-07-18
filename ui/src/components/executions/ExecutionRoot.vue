<template>
    <template v-if="ready">
        <execution-root-top-bar :route-info="routeInfo" />
        <tabs
            :route-name="$route.params && $route.params.id ? 'executions/update': ''"
            @follow="follow"
            :tabs="tabs"
        />
    </template>
    <div v-else class="full-space" v-loading="!ready" />
</template>

<script>
    import Gantt from "./Gantt.vue";
    import Overview from "./Overview.vue";
    import Logs from "./Logs.vue";
    import Topology from "./Topology.vue";
    import ExecutionOutput from "./outputs/Wrapper.vue";
    import RouteContext from "../../mixins/routeContext";
    import {mapState} from "vuex";
    import {mapStores} from "pinia";
    import {useCoreStore} from "../../stores/core";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import Tabs from "../../components/Tabs.vue";
    import ExecutionRootTopBar from "./ExecutionRootTopBar.vue";
    import DemoAuditLogs from "../demo/AuditLogs.vue";

    import ExecutionMetric from "./ExecutionMetric.vue";
    import ExecutionDependencies from "./ExecutionDependencies.vue";

    import throttle from "lodash/throttle";
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
                throttledExecutionUpdate: throttle(function (executionEvent) {
                    let execution = JSON.parse(executionEvent.data);
                    const flow = this.executionsStore.flow

                    if ((!flow ||
                        execution.flowId !== flow.id ||
                        execution.namespace !== flow.namespace ||
                        execution.flowRevision !== flow.revision)
                    ) {
                        this.executionsStore.loadFlowForExecutionByExecutionId(
                            {
                                id: execution.id,
                                revision: this.$route.query.revision
                            }
                        );
                    }

                    this.executionsStore.execution = execution;
                }, 500)
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
            $route(newValue, oldValue) {
                this.executionsStore.taskRun = undefined;
                if (oldValue.name === newValue.name && this.previousExecutionId !== this.$route.params.id) {
                    this.follow()
                }
                // if we change the execution id, we need to close the sse
                if (this.executionsStore.execution && this.$route.params.id != this.executionsStore.execution.id) {
                    this.closeSSE();
                    window.removeEventListener("popstate", this.follow)
                    this.executionsStore.execution = undefined;
                    this.$store.commit("flow/setFlow", undefined);
                    this.$store.commit("flow/setFlowGraph", undefined);
                }
            },
        },
        methods: {
            follow() {
                this.closeSSE();
                this.previousExecutionId = this.$route.params.id;
                this.executionsStore.followExecution(this.$route.params)
                    .then(sse => {
                        this.sse = sse;
                        this.sse.onmessage = (executionEvent) => {
                            const isEnd = executionEvent && executionEvent.lastEventId === "end";
                            if (isEnd) {
                                this.closeSSE();
                            }
                            // we are receiving a first "fake" event to force initializing the connection: ignoring it
                            if (executionEvent.lastEventId !== "start") {
                                this.throttledExecutionUpdate(executionEvent);
                            }
                            if (isEnd) {
                                this.throttledExecutionUpdate.flush();
                            }
                        }
                        // sse.onerror doesnt return the details of the error
                        // but as our emitter can only throw an error on 404
                        // we can safely assume that the error is a 404
                        // if execution is not defined
                        this.sse.onerror = () => {
                            if (!this.executionsStore.execution) {
                                this.coreStore.message = {
                                    variant: "error",
                                    title: this.$t("error"),
                                    message: this.$t("errors.404.flow or execution"),
                                };
                            } else {
                                this.coreStore.message = {
                                    variant: "error",
                                    title: this.$t("error"),
                                    message: this.$t("something_went_wrong.loading_execution"),
                                };
                            }
                        }
                    });
            },
            closeSSE() {
                if (this.sse) {
                    this.sse.close();
                    this.sse = undefined;
                }
            },
            getTabs() {
                const title = title => this.$t(title);
                return [
                    {
                        name: undefined,
                        component: Overview,
                        title: title("overview"),
                    },
                    {
                        name: "gantt",
                        component: Gantt,
                        title: title("gantt")
                    },
                    {
                        name: "logs",
                        component: Logs,
                        title: title("logs")
                    },
                    {
                        name: "topology",
                        component: Topology,
                        title: title("topology")
                    },
                    {
                        name: "outputs",
                        component: ExecutionOutput,
                        title: title("outputs"),
                        maximized: true
                    },
                    {
                        name: "metrics",
                        component: ExecutionMetric,
                        title: title("metrics")
                    },
                    {
                        name: "dependencies",
                        component: ExecutionDependencies,
                        title: title("dependencies"),
                        props: {
                            isReadOnly: true,
                        },
                    },
                    {
                        name: "auditlogs",
                        component: DemoAuditLogs,
                        title: title("auditlogs"),
                        maximized: true,
                        locked: true
                    }
                ];
            },
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
            this.closeSSE();
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