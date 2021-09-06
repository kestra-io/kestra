<template>
    <div>
        <b-card v-if="ready" no-body>
            <b-tabs card @activate-tab="onTabChange">
                <b-tab
                    v-for="tab in tabs()"
                    :key="tab.tab"
                    :active="$route.query.tab === tab.tab"
                    :title="tab.title"
                    :class="tab.class"
                    lazy
                >
                    <b-card-text>
                        <div :is="tab.tab" ref="currentTab" :prevent-route-info="true" />
                    </b-card-text>
                </b-tab>
            </b-tabs>
        </b-card>
    </div>
</template>
<script>
    import Topology from "./Topology";
    import Schedule from "./Schedule";
    import DataSource from "./DataSource";
    import Revisions from "./Revisions";
    import ExecutionConfiguration from "./ExecutionConfiguration";
    import BottomLine from "../layout/BottomLine";
    import FlowActions from "./FlowActions";
    import FlowLogs from "./FlowLogs";
    import FlowExecutions from "./FlowExecutions";
    import RouteContext from "../../mixins/routeContext";
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";

    export default {
        mixins: [RouteContext],
        components: {
            Topology,
            Schedule,
            BottomLine,
            DataSource,
            FlowActions,
            FlowExecutions,
            ExecutionConfiguration,
            Revisions,
            FlowLogs
        },
        data() {
            return {
                tabIndex: undefined,
                checkUnsaved: true,
            };
        },
        watch: {
            $route() {
                this.load()
            }
        },
        created() {
            this.load();
        },
        methods: {
            load() {
                this.$store.dispatch("flow/loadFlow", this.$route.params).then(() => {
                    if (this.flow) {
                        this.$store.dispatch("flow/loadGraph", this.flow);
                    }
                });
            },
            navigateTab(index) {
                this.$router
                    .push({
                        name: "flows/update",
                        params: this.$route.params,
                        query: {tab: this.tabs()[index].tab}
                    })
                    .finally(() => {
                        this.checkUnsaved = true;
                    })
            },
            tabs() {
                const tabs = [
                    {
                        tab: "topology",
                        title: this.$t("topology"),
                        class: "p-0"
                    },
                ];

                if (this.user && this.flow && this.user.isAllowed(permission.EXECUTION, action.READ, this.flow.namespace)) {
                    tabs.push({
                        tab: "flow-executions",
                        title: this.$t("executions")
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.flow.namespace)) {
                    tabs.push({
                        tab: "execution-configuration",
                        title: this.$t("launch execution")
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.FLOW, action.READ, this.flow.namespace)) {
                    tabs.push({
                        tab: "data-source",
                        title: this.$t("source"),
                        class: "p-0"
                    });

                    tabs.push({
                        tab: "schedule",
                        title: this.$t("schedule"),
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.FLOW, action.READ, this.flow.namespace)) {
                    tabs.push({
                        tab: "revisions",
                        title: this.$t("revisions")
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.EXECUTION, action.READ, this.flow.namespace)) {
                    tabs.push({
                        tab: "flow-logs",
                        title: this.$t("logs")
                    });
                }

                return tabs;
            },
            hasUnsavedChanged() {
                return this.$refs.currentTab &&
                    this.$refs.currentTab[0].$children &&
                    this.$refs.currentTab[0].$children[0] &&
                    this.$refs.currentTab[0].$children[0].hasUnsavedChanged &&
                    this.$refs.currentTab[0].$children[0].hasUnsavedChanged();
            },
            onTabChange(newTabIndex, prevTabIndex, bvEvent) {
                if (this.checkUnsaved === true && this.hasUnsavedChanged()) {
                    bvEvent.preventDefault();

                    this.$toast().unsavedConfirm(
                        () => {
                            this.checkUnsaved = false;
                            this.navigateTab(newTabIndex)
                        },
                        () => {
                            this.checkUnsaved = true;
                        }
                    )
                } else if (this.checkUnsaved !== false) {
                    this.navigateTab(newTabIndex)
                }
            },
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
                        },
                        {
                            label: this.$route.params.id,
                            link: {
                                name: "flows/update",
                                params: {
                                    namespace: this.$route.params.namespace,
                                    id: this.$route.params.id
                                }
                            }
                        }
                    ]
                };
            },
            ready() {
                return this.flow !== undefined;
            }
        },
        destroyed () {
            this.$store.commit("flow/setFlow", undefined)
        }
    };
</script>
