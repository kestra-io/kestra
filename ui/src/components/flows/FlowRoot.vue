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
import Overview from "./Overview";
import DataSource from "./DataSource";
import ExecutionConfiguration from "./ExecutionConfiguration";
import BottomLine from "../layout/BottomLine";
import FlowActions from "./FlowActions";
import Executions from "../executions/Executions";
import RouteContext from "../../mixins/routeContext";
import { mapState } from "vuex";
import permission from "../../models/permission";
import action from "../../models/action";

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
                name: "flowEdit",
                params: this.$route.params,
                query: { tab }
            });
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
        },
        tabs() {
            const title = title => this.$t(title);
            const tabs = [
                {
                    tab: "overview",
                    title: title("overview")
                },
            ];

            if (this.user && this.flow && this.user.isAllowed(permission.EXECUTION, action.READ, this.flow.namespace)) {
                tabs.push({
                    tab: "executions",
                    title: title("executions")
                });
            }

            if (this.user && this.flow && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.flow.namespace)) {
                tabs.push({
                    tab: "execution-configuration",
                    title: title("trigger"),
                });
            }

            tabs.push({
                tab: "data-source",
                title: title("source"),
                class: "p-0"
            });

            return tabs;
        }
    },
    destroyed () {
        this.$store.commit('flow/setFlow', undefined)
    }
};
</script>
