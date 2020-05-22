<template>
    <div>
        <b-card no-body>
            <b-tabs card>
                <b-tab
                    v-for="tab in tabs"
                    :key="tab.tab"
                    @click="setTab(tab.tab)"
                    :active="$route.query.tab === tab.tab"
                    :title="tab.title"
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
import Overview from "./Overview";
import DataSource from "./DataSource";
import ExecutionConfiguration from "./ExecutionConfiguration";
import BottomLine from "../layout/BottomLine";
import FlowActions from "./FlowActions";
import Executions from "../executions/Executions";
import RouteContext from "../../mixins/routeContext";
import { mapState } from "vuex";
export default {
    mixins: [RouteContext],
    components: {
        Overview,
        BottomLine,
        DataSource,
        FlowActions,
        Executions,
        ExecutionConfiguration
    },
    created() {
        this.$store.dispatch("flow/loadFlow", this.$route.params).then(() => {
            if (this.flow) {
                this.$store.dispatch("flow/loadTree", this.flow);
            }
        });
    },
    methods: {
        setTab(tab) {
            this.$router.push({
                name: "flow",
                params: this.$route.params,
                query: { tab }
            });
        }
    },
    computed: {
        ...mapState("flow", ["flow"]),
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
                            name: "flow",
                            params: {
                                namespace: this.$route.params.namespace,
                                id: this.$route.params.id
                            }
                        }
                    }
                ]
            };
        },
        tabs() {
            const title = title => this.$t(title).capitalize();
            return [
                {
                    tab: "overview",
                    title: title("overview")
                },
                {
                    tab: "executions",
                    title: title("executions")
                },
                {
                    tab: "execution-configuration",
                    title: title("trigger")
                },
                {
                    tab: "data-source",
                    title: title("source")
                }
            ];
        }
    },
    destroyed () {
        this.$store.commit('flow/setFlow', undefined)
    }

};
</script>
