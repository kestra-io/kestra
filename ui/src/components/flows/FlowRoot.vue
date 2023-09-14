<template>
    <div>
        <div v-if="ready">
            <tabs @expand-subflow="updateExpandedSubflows" route-name="flows/update" ref="currentTab" :tabs="tabs" />
            <bottom-line v-if="displayBottomLine()">
                <ul>
                    <li>
                        <template v-if="isAllowedEdit">
                            <el-button :icon="Pencil" size="large" @click="editFlow">
                                {{ $t('edit flow') }}
                            </el-button>
                        </template>
                    </li>
                    <li>
                        <trigger-flow v-if="flow" :disabled="flow.disabled" :flow-id="flow.id" :namespace="flow.namespace" />
                    </li>
                </ul>
            </bottom-line>
        </div>
    </div>
</template>

<script setup>
    import Pencil from "vue-material-design-icons/Pencil.vue";
</script>

<script>
    import Topology from "./Topology.vue";
    import FlowRevisions from "./FlowRevisions.vue";
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
    import FlowDependencies from "./FlowDependencies.vue";
    import FlowMetrics from "./FlowMetrics.vue";
    import FlowEditor from "./FlowEditor.vue";
    import FlowTriggers from "./FlowTriggers.vue";
    import {apiUrl} from "override/utils/route";

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
                previousFlow: undefined,
                depedenciesCount: undefined,
                expandedSubflows: []
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
                            this.$store.dispatch("flow/loadGraph", {
                                flow: this.flow
                            });
                            this.$http
                                .get(`${apiUrl(this.$store)}/flows/${this.flow.namespace}/${this.flow.id}/dependencies`)
                                .then(response => {
                                    this.depedenciesCount = response.data && response.data.nodes ? response.data.nodes.length - 1 : 0;
                                })
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
                        props: {
                            isReadOnly: true,
                            expandedSubflows: this.expandedSubflows
                        }
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

                if (this.user && this.flow && this.user.isAllowed(permission.FLOW, action.READ, this.flow.namespace)) {
                    tabs.push({
                        name: "editor",
                        component: FlowEditor,
                        title: this.$t("editor"),
                        props: {
                            expandedSubflows: this.expandedSubflows
                        }
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.FLOW, action.READ, this.flow.namespace)) {
                    tabs.push({
                        name: "revisions",
                        component: FlowRevisions,
                        title: this.$t("revisions")
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.FLOW, action.READ, this.flow.namespace)) {
                    tabs.push({
                        name: "triggers",
                        component: FlowTriggers,
                        title: this.$t("triggers"),
                        disabled: !this.flow.triggers
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.EXECUTION, action.READ, this.flow.namespace)) {
                    tabs.push({
                        name: "logs",
                        component: FlowLogs,
                        title: this.$t("logs"),
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.EXECUTION, action.READ, this.flow.namespace)) {
                    tabs.push({
                        name: "metrics",
                        component: FlowMetrics,
                        title: this.$t("metrics"),
                    });
                }
                if (this.user && this.flow && this.user.isAllowed(permission.FLOW, action.READ, this.flow.namespace)){
                    tabs.push({
                        name: "dependencies",
                        component: FlowDependencies,
                        title: this.$t("dependencies"),
                        count: this.depedenciesCount
                    })
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
                return name != null && this.canExecute && name !== "executions" && name !== "source" && name !== "schedule" && name !== "editor";
            },
            editFlow() {
                this.$router.push({name:"flows/update", params: {
                    namespace: this.flow.namespace,
                    id: this.flow.id,
                    tab: "editor"
                }})
            },
            updateExpandedSubflows(expandedSubflows) {
                this.expandedSubflows = expandedSubflows;
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
            isAllowedEdit() {
                return this.user.isAllowed(permission.FLOW, action.UPDATE, this.flow.namespace);
            },
            canExecute() {
                if(this.flow) {
                    return this.user.isAllowed(permission.EXECUTION, action.CREATE, this.flow.namespace)
                }
                return false;
            }
        },
        unmounted () {
            this.$store.commit("flow/setFlow", undefined)
        }
    };
</script>
