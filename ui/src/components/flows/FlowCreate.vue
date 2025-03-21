<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="full-container">
        <editor-view
            v-if="flow"
            :flow-id="flow?.id"
            :namespace="flow?.namespace"
            :flow-validation="flowValidation"
            :flow-graph="flowGraph"
            :is-read-only="false"
            is-creating
            is-dirty
            :flow="flow"
            :next-revision="1"
        />
    </section>
</template>

<script>
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";
    import EditorView from "../inputs/EditorView.vue";
    import {mapGetters, mapMutations, mapState} from "vuex";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";

    import {getRandomFlowID} from "../../../scripts/product/flow";

    export default {
        mixins: [RouteContext],
        components: {
            EditorView,
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
                if (this.$route.query.copy && this.flow){
                    this.$store.commit("flow/setFlowYaml", this.flow.source);
                } else if (blueprintId && blueprintSource) {
                    this.$store.commit("flow/setFlowYaml", await this.$store.dispatch("blueprints/getBlueprintSource", {type: blueprintSource, kind: "flow", id: blueprintId}));
                } else {
                    const selectedNamespace = this.$route.query.namespace || "company.team";
                    this.$store.commit("flow/setFlowYaml", `id: ${getRandomFlowID()}
namespace: ${selectedNamespace}

tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello World! 🚀`);
                }

                this.$store.commit("flow/setFlow", {...YAML_UTILS.parse(this.flowYaml), source: this.flowYaml});
                this.$store.dispatch("flow/initYamlSource", {});
            }
        },
        computed: {
            ...mapState("flow", ["flowGraph"]),
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
