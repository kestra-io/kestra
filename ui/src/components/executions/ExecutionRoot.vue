<template>
    <div>
        <div v-if="ready">
            <tabs route-name="executions/update" @follow="follow" :tabs="tabs" />
        </div>
        <bottom-line>
            <ul class="navbar-nav ml-auto" v-hotkey="keymap">
                <li class="nav-item">
                    <template v-if="isAllowedTrigger">
                        <trigger-flow :flow-id="$route.params.flowId" :namespace="$route.params.namespace" />
                    </template>

                    <template v-if="isAllowedEdit">
                        <b-button @click="editFlow">
                            <kicon :tooltip="'(Ctrl + Shift + e)'">
                                <pencil /> {{ $t('edit flow') }}
                            </kicon>
                        </b-button>
                    </template>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>
<script>
    import Gantt from "./Gantt";
    import Overview from "./Overview";
    import Logs from "./Logs";
    import Topology from "./Topology";
    import ExecutionOutput from "./ExecutionOutput";
    import BottomLine from "../layout/BottomLine";
    import TriggerFlow from "../flows/TriggerFlow";
    import RouteContext from "../../mixins/routeContext";
    import {mapState} from "vuex";
    import Pencil from "vue-material-design-icons/Pencil";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import Kicon from "../Kicon"
    import Tabs from "../../components/Tabs";

    export default {
        mixins: [RouteContext],
        components: {
            BottomLine,
            TriggerFlow,
            Pencil,
            Kicon,
            Tabs,
        },
        data() {
            return {
                sse: undefined,
                previousExecutionId: undefined
            };
        },
        created() {
            this.follow();
            window.addEventListener("popstate", this.follow)
        },
        mounted () {
            this.previousExecutionId = this.$route.params.id
        },
        watch: {
            $route () {
                this.$store.commit("execution/setTaskRun", undefined);
                this.$store.commit("execution/setTask", undefined);
                if (this.previousExecutionId !== this.$route.params.id) {
                    this.follow()
                }
            },
        },
        methods: {
            follow() {
                const self = this;
                this.closeSSE();
                this.$store
                    .dispatch("execution/followExecution", this.$route.params)
                    .then(sse => {
                        this.sse = sse
                        this.sse.onmessage = (event) => {
                            if (event && event.lastEventId === "end") {
                                self.closeSSE();
                            }

                            this.$store.commit("execution/setExecution", JSON.parse(event.data));
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
                        title: title("topology"),
                        bodyClass: {"p-0" : true}
                    },
                    {
                        name: "outputs",
                        component: ExecutionOutput,
                        title: title("outputs")
                    }
                ];
            },
            editFlow() {
                this.$router.push({name:"flows/update", params: {
                    namespace: this.$route.params.namespace,
                    id: this.$route.params.flowId,
                    tab: "source"
                }})
            },
        },
        computed: {
            ...mapState("execution", ["execution"]),
            ...mapState("auth", ["user"]),
            keymap () {
                return {
                    "ctrl+shift+e": this.editFlow,
                }
            },
            tabs() {
                return this.getTabs();
            },
            routeInfo() {
                const ns = this.$route.params.namespace;
                return {
                    title: this.$t("execution"),
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
                            label: `${ns}.${this.$route.params.flowId}`,
                            link: {
                                name: "flows/update",
                                params: {
                                    namespace: ns,
                                    id: this.$route.params.flowId
                                }
                            }
                        },
                        {
                            label: this.$t("executions"),
                            link: {
                                name: "flows/update",
                                params: {
                                    namespace: ns,
                                    id: this.$route.params.flowId
                                },
                                query: {
                                    tab: "executions"
                                }
                            }
                        },
                        {
                            label: this.$route.params.id,
                            link: {
                                name: "executions/update"
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
            ready() {
                return this.execution !== undefined;
            }
        },
        beforeDestroy() {
            this.closeSSE();
            window.removeEventListener("popstate", this.follow)
            this.$store.commit("execution/setExecution", undefined);
        }
    };
</script>
