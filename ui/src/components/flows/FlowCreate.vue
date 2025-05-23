<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="full-container">
        <MultiPanelEditorView v-if="flow" />
    </section>
</template>

<script>
    import {mapGetters, mapMutations, mapState} from "vuex";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import MultiPanelEditorView from "./MultiPanelEditorView.vue";

    import {getRandomFlowID} from "../../../scripts/product/flow";

    export default {
        mixins: [RouteContext],
        components: {
            MultiPanelEditorView,
            TopNavBar
        },

        created() {
            this.$store.commit("flow/setIsCreating", true);
            if (this.$route.query.reset) {
                localStorage.setItem("tourDoneOrSkip", undefined);
                this.$store.commit("core/setGuidedProperties", {tourStarted: true});
                this.$tours["guidedTour"]?.start();
            }
            this.setupFlow()
            this.closeAllTabs()
        },
        beforeUnmount() {
            this.$store.commit("flow/setFlowValidation", undefined);
        },
        methods: {
            ...mapMutations("editor", ["closeAllTabs"]),

            async setupFlow() {
                const blueprintId = this.$route.query.blueprintId;
                const blueprintSource = this.$route.query.blueprintSource;
                let flowYaml = ""
                if (this.$route.query.copy && this.flow){
                    flowYaml = this.flow.source;
                } else if (blueprintId && blueprintSource) {
                    flowYaml = await this.$store.dispatch("blueprints/getBlueprintSource", {type: blueprintSource, kind: "flow", id: blueprintId});
                } else {
                    const selectedNamespace = this.$route.query.namespace || "company.team";
                    flowYaml = `id: ${getRandomFlowID()}
namespace: ${selectedNamespace}

tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello World! 🚀`;
                }

                this.$store.commit("flow/setFlowYaml", flowYaml);
                this.$store.commit("flow/setFlowYamlBeforeAdd", flowYaml);

                this.$store.commit("flow/setFlow", {...YAML_UTILS.parse(this.flowYaml), source: this.flowYaml});
                this.$store.dispatch("flow/initYamlSource", {});
            }
        },
        computed: {
            ...mapState("flow", ["flowGraph", "flowYaml"]),
            ...mapState("auth", ["user"]),
            ...mapState("plugin", ["pluginSingleList", "pluginsDocumentation"]),
            ...mapGetters("core", ["guidedProperties"]),
            ...mapGetters("flow", ["flow", "flowValidation", "flowYaml"]),
            routeInfo() {
                return {
                    title: this.$t("flows")
                };
            },
            flowParsed() {
                return YAML_UTILS.parse(this.source);
            }
        },
        beforeRouteLeave(to, from, next) {
            this.$store.commit("flow/setFlow", null);
            next();
        }
    };
</script>
