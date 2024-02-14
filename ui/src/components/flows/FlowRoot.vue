<template>
    <template v-if="ready">
        <top-nav-bar :breadcrumb="routeInfo.breadcrumb">
            <template #title>
                <template v-if="deleted">
                    <Alert class="text-warning me-2" />Deleted:&nbsp;
                </template>
                <Lock v-else-if="!isAllowedEdit" class="me-2 gray-700" />
                <span :class="{'body-color': deleted}">{{ routeInfo.title }}</span>
            </template>
            <template #additional-right v-if="displayButtons()">
                <ul>
                    <li v-if="deleted">
                        <el-button :icon="BackupRestore" @click="restoreFlow()">
                            {{ $t("restore") }}
                        </el-button>
                    </li>
                    <li v-if="isAllowedEdit && !deleted && activeTabName() !== 'editor'">
                        <el-button :icon="Pencil" @click="editFlow" :disabled="deleted">
                            {{ $t("edit flow") }}
                        </el-button>
                    </li>
                    <li v-if="flow && !deleted">
                        <trigger-flow
                            type="primary"
                            :disabled="flow.disabled"
                            :flow-id="flow.id"
                            :namespace="flow.namespace"
                        />
                    </li>
                </ul>
            </template>
        </top-nav-bar>
        <div>
            <tabs @expand-subflow="updateExpandedSubflows" route-name="flows/update" ref="currentTab" :tabs="tabs" />
        </div>
    </template>
</template>

<script setup>
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import BackupRestore from "vue-material-design-icons/BackupRestore.vue";
    import Alert from "vue-material-design-icons/Alert.vue";
    import Lock from "vue-material-design-icons/Lock.vue";
</script>

<script>
    import Topology from "./Topology.vue";
    import FlowRevisions from "./FlowRevisions.vue";
    import FlowLogs from "./FlowLogs.vue";
    import FlowExecutions from "./FlowExecutions.vue";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import Tabs from "../Tabs.vue";
    import TriggerFlow from "../../components/flows/TriggerFlow.vue";
    import Overview from "./Overview.vue";
    import FlowDependencies from "./FlowDependencies.vue";
    import FlowMetrics from "./FlowMetrics.vue";
    import FlowEditor from "./FlowEditor.vue";
    import FlowTriggers from "./FlowTriggers.vue";
    import {apiUrl} from "override/utils/route";
    import yamlUtils from "../../utils/yamlUtils";

    export default {
        mixins: [RouteContext],
        components: {
            TriggerFlow,
            Tabs,
            TopNavBar
        },
        data() {
            return {
                tabIndex: undefined,
                previousFlow: undefined,
                dependenciesCount: undefined,
                expandedSubflows: [],
                deleted: false
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
                    const query = {...this.$route.query, allowDeleted: true};
                    return this.$store.dispatch("flow/loadFlow", {...this.$route.params, ...query}).then(() => {
                        if (this.flow) {
                            this.deleted = this.flow.deleted;
                            this.previousFlow = this.flowKey();
                            this.$store.dispatch("flow/loadGraph", {
                                flow: this.flow
                            });
                            this.$http
                                .get(`${apiUrl(this.$store)}/flows/${this.flow.namespace}/${this.flow.id}/dependencies`)
                                .then(response => {
                                    this.dependenciesCount = response.data && response.data.nodes ? [...new Set(response.data.nodes.map(r => r.uid))].length - 1 : 0;
                                })
                        }
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
                            title: this.$t("overview")
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
                            expandedSubflows: this.expandedSubflows,
                            isReadOnly: this.deleted || !this.isAllowedEdit
                        },
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
                        title: this.$t("logs")
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.EXECUTION, action.READ, this.flow.namespace)) {
                    tabs.push({
                        name: "metrics",
                        component: FlowMetrics,
                        title: this.$t("metrics")
                    });
                }
                if (this.user && this.flow && this.user.isAllowed(permission.FLOW, action.READ, this.flow.namespace)) {
                    tabs.push({
                        name: "dependencies",
                        component: FlowDependencies,
                        title: this.$t("dependencies"),
                        count: this.dependenciesCount
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
            displayButtons() {
                const name = this.activeTabName();
                return name != null && this.canExecute;
            },
            editFlow() {
                this.$router.push({
                    name: "flows/update", params: {
                        namespace: this.flow.namespace,
                        id: this.flow.id,
                        tab: "editor",
                        tenant: this.$route.params.tenant
                    }
                })
            },
            updateExpandedSubflows(expandedSubflows) {
                this.expandedSubflows = expandedSubflows;
            },
            restoreFlow() {
                this.$store.dispatch("flow/createFlow", {flow: yamlUtils.deleteMetadata(this.flow.source, "deleted")})
                    .then((response) => {
                        this.$toast().saved(response.id);
                        this.$store.dispatch("core/isUnsaved", false);
                        this.$router.go();
                    })
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
                return this.user !== undefined && this.flow !== undefined;
            },
            isAllowedEdit() {
                if(!this.flow || !this.user) {
                    return false;
                }

                return this.user.isAllowed(permission.FLOW, action.UPDATE, this.flow.namespace);
            },
            canExecute() {
                if (this.flow) {
                    return this.user.isAllowed(permission.EXECUTION, action.CREATE, this.flow.namespace)
                }
                return false;
            }
        },
        unmounted() {
            this.$store.commit("flow/setFlow", undefined)
            this.$store.commit("flow/setFlowGraph", undefined)
        }
    };
</script>
<style lang="scss" scoped>
    .gray-700 {
        color: var(--bs-secondary-color);
    }
    .body-color {
        color: var(--bs-body-color);
    }
</style>