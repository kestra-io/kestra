<template>
    <div>
        <div v-if="ready">
            <tabs route-name="flows/update" ref="currentTab" :tabs="tabs" @hook:mounted="mounted = true" />
            <bottom-line v-if="mounted && displayBottomLine()">
                <ul>
                    <li>
                        <trigger-flow v-if="flow" :disabled="flow.disabled" :flow-id="flow.id" :namespace="flow.namespace" />
                    </li>
                </ul>
            </bottom-line>
        </div>
    </div>
</template>
<script>
    import Topology from "./Topology.vue";
    import Schedule from "./Schedule.vue";
    import FlowSource from "./FlowSource.vue";
    import FlowRevisions from "./FlowRevisions.vue";
    import FlowRun from "./FlowRun.vue";
    import FlowLogs from "./FlowLogs.vue";
    import FlowExecutions from "./FlowExecutions.vue";
    import RouteContext from "../../mixins/routeContext";
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import Tabs from "../Tabs.vue";
    import BottomLine from "../../components/layout/BottomLine.vue";
    import TriggerFlow from "../../components/flows/TriggerFlow.vue";
    import Overview from "./Overview.vue";

    export default {
        mixins: [RouteContext],
        components: {
            BottomLine,
            TriggerFlow,
            Tabs
        },
        data() {
            return {
                tabIndex: undefined,
                mounted: false,
                previousFlow: undefined
            };
        },
        watch: {
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name) {
                    this.load()
                }
            }
        },
        created() {
            this.load();
        },
        methods: {
            load() {
                if ((this.flow === undefined || this.previousFlow !== this.flowKey())) {
                    return this.$store.dispatch("flow/loadFlow", this.$route.params).then(() => {
                        if (this.flow) {
                            this.previousFlow = this.flowKey();
                            this.$store.dispatch("flow/loadGraph", this.flow);
                        }
                    });
                }

            },
            flowKey() {
                return this.$route.params.namespace +  "/" + this.$route.params.id;
            },
            getTabs() {
                let tabs = [
                    {
                        name: undefined,
                        component: Topology,
                        title: this.$t("topology"),
                    },
                ];

                if (this.user.hasAny(permission.EXECUTION)) {
                    tabs[0].name = "topology";

                    tabs = [
                        {
                            name: undefined,
                            component: Overview,
                            title: this.$t("overview"),
                        },
                    ].concat(tabs)
                }

                if (this.user && this.flow && this.user.isAllowed(permission.EXECUTION, action.READ, this.flow.namespace)) {
                    tabs.push({
                        name: "executions",
                        component: FlowExecutions,
                        title: this.$t("executions"),
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.flow.namespace)) {
                    tabs.push({
                        name: "execute",
                        component: FlowRun,
                        title: this.$t("launch execution")
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.FLOW, action.READ, this.flow.namespace)) {
                    tabs.push({
                        name: "source",
                        component: FlowSource,
                        title: this.$t("source"),
                    });

                    tabs.push({
                        name: "schedule",
                        component: Schedule,
                        title: this.$t("schedule"),
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.FLOW, action.READ, this.flow.namespace)) {
                    tabs.push({
                        name: "revisions",
                        component: FlowRevisions,
                        title: this.$t("revisions")
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.EXECUTION, action.READ, this.flow.namespace)) {
                    tabs.push({
                        name: "logs",
                        component: FlowLogs,
                        title: this.$t("logs"),
                    });
                }

                return tabs;
            },
            activeTabName() {
                if (this.$refs.currentTab) {
                    return this.$refs.currentTab.activeTab.name || "home";
                }

                return null;
            },
            displayBottomLine() {
                const name = this.activeTabName();
                return name != null &&  this.canExecute && name !== "execute" && name !== "source" && name !== "schedule";
            }
        },
        computed: {
            ...mapState("flow", ["flow"]),
            ...mapState("auth", ["user"]),
            routeInfo() {
                return {
                    title: this.$route.params.id,
                    breadcrumb: [
                        {
                            label: this.$t("flows"),
                            link: {
                                name: "flows/list"
                            }
                        },
                        {
                            label: this.$route.params.namespace,
                            link: {
                                name: "flows/list",
                                query: {
                                    namespace: this.$route.params.namespace
                                }
                            }
                        }
                    ]
                };
            },
            tabs() {
                return this.getTabs();
            },
            ready() {
                return this.flow !== undefined;
            },
            canExecute() {
                return this.user.isAllowed(permission.EXECUTION, action.CREATE, this.flow.namespace)
            },
        },
        unmounted () {
            this.$store.commit("flow/setFlow", undefined)
        }
    };
</script>
