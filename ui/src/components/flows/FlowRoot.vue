<template>
    <div v-if="ready">
        <tabs route-name="flows/update" ref="currentTab" :tabs="tabs" />
    </div>
</template>
<script>
    import Topology from "./Topology";
    import Schedule from "./Schedule";
    import FlowSource from "./FlowSource";
    import FlowRevisions from "./FlowRevisions";
    import FlowRun from "./FlowRun";
    import FlowLogs from "./FlowLogs";
    import FlowExecutions from "./FlowExecutions";
    import RouteContext from "../../mixins/routeContext";
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import Tabs from "../Tabs";
    import UnsavedChange from "../../mixins/unsavedChange";

    export default {
        mixins: [RouteContext, UnsavedChange],
        components: {
            Tabs
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
            UnsavedChange.methods.created.call(this);
            this.load();
        },
        beforeDestroy() {
            UnsavedChange.methods.beforeDestroy.call(this);
        },
        methods: {
            load() {
                return this.$store.dispatch("flow/loadFlow", this.$route.params).then(() => {
                    if (this.flow) {
                        this.$store.dispatch("flow/loadGraph", this.flow);
                    }
                });
            },
            getTabs() {
                const tabs = [
                    {
                        name: undefined,
                        component: Topology,
                        title: this.$t("topology"),
                    },
                ];

                if (this.user && this.flow && this.user.isAllowed(permission.EXECUTION, action.READ, this.flow.namespace)) {
                    tabs.push({
                        name: "executions",
                        component: FlowExecutions,
                        title: this.$t("executions"),
                        background: false,
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
                        background: false,
                    });
                }

                return tabs;
            },
            hasUnsavedChanged() {
                return this.$refs.currentTab &&
                    this.$refs.currentTab.$refs.tabContent &&
                    this.$refs.currentTab.$refs.tabContent.$children[0] &&
                    this.$refs.currentTab.$refs.tabContent.$children[0].hasUnsavedChanged &&
                    this.$refs.currentTab.$refs.tabContent.$children[0].hasUnsavedChanged();
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
                        }
                    ]
                };
            },
            tabs() {
                return this.getTabs();
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
