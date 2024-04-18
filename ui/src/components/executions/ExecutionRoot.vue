<template>
    <top-nav-bar :title="routeInfo?.title" :breadcrumb="routeInfo?.breadcrumb">
        <template #additional-right v-if="canDelete || isAllowedTrigger || isAllowedEdit">
            <ul>
                <li v-if="isAllowedEdit">
                    <a :href="`${finalApiUrl}/executions/${execution.id}`" target="_blank">
                        <el-button :icon="Api">
                            {{ $t("api") }}
                        </el-button>
                    </a>
                </li>
                <li v-if="canDelete">
                    <el-button :icon="Delete" @click="deleteExecution">
                        {{ $t("delete") }}
                    </el-button>
                </li>
                <li v-if="isAllowedEdit">
                    <el-button :icon="Pencil" @click="editFlow">
                        {{ $t("edit flow") }}
                    </el-button>
                </li>
                <li v-if="isAllowedTrigger">
                    <trigger-flow type="primary" :flow-id="$route.params.flowId" :namespace="$route.params.namespace" />
                </li>
            </ul>
        </template>
    </top-nav-bar>
    <template v-if="ready">
        <tabs
            :route-name="$route.params && $route.params.id ? 'executions/update': ''"
            @follow="follow"
            :tabs="tabs"
        />
    </template>
    <div v-else class="full-space" v-loading="!ready" />
</template>

<script setup>
    import Api from "vue-material-design-icons/Api.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
</script>

<script>
    import Gantt from "./Gantt.vue";
    import Overview from "./Overview.vue";
    import Logs from "./Logs.vue";
    import Topology from "./Topology.vue";
    import ExecutionOutput from "./ExecutionOutput.vue";
    import TriggerFlow from "../flows/TriggerFlow.vue";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import Tabs from "../../components/Tabs.vue";

    import State from "../../utils/state";
    import ExecutionMetric from "./ExecutionMetric.vue";
    import {apiUrl} from "override/utils/route"
    import throttle from "lodash/throttle";

    export default {
        mixins: [RouteContext],
        components: {
            TriggerFlow,
            Tabs,
            TopNavBar
        },
        data() {
            return {
                sse: undefined,
                previousExecutionId: undefined,
                throttledExecutionUpdate: throttle(function (executionEvent) {
                    let execution = JSON.parse(executionEvent.data);

                    if ((!this.flow ||
                        execution.flowId !== this.flow.id ||
                        execution.namespace !== this.flow.namespace ||
                        execution.flowRevision !== this.flow.revision)
                    ) {
                        this.$store.dispatch(
                            "execution/loadFlowForExecutionByExecutionId",
                            {
                                id: execution.id,
                            }
                        );
                    }

                    this.$store.commit("execution/setExecution", execution);
                }, 500)
            };
        },
        created() {
            this.follow();
            window.addEventListener("popstate", this.follow)
        },
        mounted() {
            this.previousExecutionId = this.$route.params.id
        },
        watch: {
            $route(newValue, oldValue) {
                this.$store.commit("execution/setTaskRun", undefined);
                if (oldValue.name === newValue.name && this.previousExecutionId !== this.$route.params.id) {
                    this.follow()
                }
            },
        },
        methods: {
            follow() {
                this.closeSSE();
                this.previousExecutionId = this.$route.params.id;
                this.$store
                    .dispatch("execution/followExecution", this.$route.params)
                    .then(sse => {
                        this.sse = sse;
                        this.sse.onmessage = (executionEvent) => {
                            const isEnd = executionEvent && executionEvent.lastEventId === "end";
                            if (isEnd) {
                                this.closeSSE();
                            }
                            this.throttledExecutionUpdate(executionEvent);
                            if (isEnd) {
                                this.throttledExecutionUpdate.flush();
                            }
                        }
                        // sse.onerror doesnt return the details of the error
                        // but as our emitter can only throw an error on 404
                        // we can safely assume that the error
                        this.sse.onerror = () => {
                            this.$store.dispatch("core/showMessage", {
                                variant: "error",
                                title: this.$t("error"),
                                message: this.$t("errors.404.flow or execution"),
                            });
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
                        title: title("outputs")
                    },
                    {
                        name: "metrics",
                        component: ExecutionMetric,
                        title: title("metrics")
                    }
                ];
            },
            editFlow() {
                this.$router.push({
                    name: "flows/update", params: {
                        namespace: this.$route.params.namespace,
                        id: this.$route.params.flowId,
                        tab: "editor",
                        tenant: this.$route.params.tenant
                    }
                })
            },
            deleteExecution() {
                if (this.execution) {
                    const item = this.execution;

                    let message = this.$t("delete confirm", {name: item.id});
                    if (State.isRunning(this.execution.state.current)) {
                        message += this.$t("delete execution running");
                    }

                    this.$toast()
                        .confirm(message, () => {
                            return this.$store
                                .dispatch("execution/deleteExecution", item)
                                .then(() => {
                                    return this.$router.push({
                                        name: "executions/list",
                                        tenant: this.$route.params.tenant
                                    });
                                })
                                .then(() => {
                                    this.$toast().deleted(item.id);
                                })
                        });
                }
            },
            canReadFlow() {
                return this.user.isAllowed(permission.FLOW, action.READ, this.$route.params.namespace)
            }
        },
        computed: {
            // ...mapState("flow", ["flow", "revisions"]),
            ...mapState("execution", ["execution", "flow"]),
            ...mapState("auth", ["user"]),
            finalApiUrl() {
                return apiUrl(this.$store);
            },
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
                                name: "flows/update",
                                params: {
                                    namespace: ns,
                                    id: flowId
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
                return this.user && this.execution && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.execution.namespace);
            },
            isAllowedEdit() {
                return this.user && this.execution && this.user.isAllowed(permission.FLOW, action.UPDATE, this.execution.namespace);
            },
            canDelete() {
                return this.user && this.execution && this.user.isAllowed(permission.EXECUTION, action.DELETE, this.execution.namespace);
            },
            ready() {
                return this.execution !== undefined;
            }
        },
        beforeUnmount() {
            this.closeSSE();
            window.removeEventListener("popstate", this.follow)
            this.$store.commit("execution/setExecution", undefined);
            this.$store.commit("flow/setFlow", undefined);
            this.$store.commit("flow/setFlowGraph", undefined);
        }
    };
</script>
<style>
    .full-space {
        flex: 1 1 auto;
    }
</style>