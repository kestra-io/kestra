<template>
    <div>
        <b-card no-body>
            <b-tabs card>
                <b-tab
                    v-for="tab in tabs()"
                    :key="tab.tab"
                    @click="setTab(tab.tab)"
                    :active="$route.query.tab === tab.tab"
                    :title="tab.title"
                    :class="tab.class"
                    lazy
                >
                    <b-card-text>
                        <div :is="tab.tab" :prevent-route-info="true" />
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
    import Logs from "../logs/LogsWrapper";
    import Executions from "../executions/Executions";
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
            Executions,
            ExecutionConfiguration,
            Revisions,
            Logs
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
            setTab(tab) {
                this.$router.push({
                    name: "flowEdit",
                    params: this.$route.params,
                    query: {tab}
                });
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
                        tab: "executions",
                        title: this.$t("executions")
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.flow.namespace)) {
                    tabs.push({
                        tab: "execution-configuration",
                        title: this.$t("launch execution")
                    });
                }

                if (this.user && this.flow && this.user.isAllowed(permission.FLOW, action.UPDATE, this.flow.namespace)) {
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

                if (this.user && this.flow && this.user.isAllowed(permission.FLOW, action.READ, this.flow.namespace)) {
                    tabs.push({
                        tab: "logs",
                        title: this.$t("logs")
                    });
                }

                return tabs;
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
                                name: "flowsList"
                            }
                        },
                        {
                            label: this.$route.params.namespace,
                            link: {
                                name: "flowsList",
                                query: {
                                    namespace: this.$route.params.namespace
                                }
                            }
                        },
                        {
                            label: this.$route.params.id,
                            link: {
                                name: "flowEdit",
                                params: {
                                    namespace: this.$route.params.namespace,
                                    id: this.$route.params.id
                                }
                            }
                        }
                    ]
                };
            }
        },
        destroyed () {
            this.$store.commit("flow/setFlow", undefined)
        }
    };
</script>
