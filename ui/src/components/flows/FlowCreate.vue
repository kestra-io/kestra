<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="full-container">
        <editor-view
            v-if="source"
            :flow-id="flowParsed?.id"
            :namespace="flowParsed?.namespace"
            :is-creating="true"
            :flow-validation="flowValidation"
            :flow-graph="flowGraph"
            :is-read-only="false"
            :is-dirty="true"
            :flow="sourceWrapper"
            :next-revision="1"
        />
    </section>
</template>

<script>
    import {YamlUtils} from "@kestra-io/ui-libs";
    import EditorView from "../inputs/EditorView.vue";
    import {mapGetters, mapMutations, mapState} from "vuex";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";

    export default {
        mixins: [RouteContext],
        components: {
            EditorView,
            TopNavBar
        },
        data() {
            return {
                source: null
            }
        },
        created() {
            if (this.$route.query.reset) {
                localStorage.setItem("tourDoneOrSkip", undefined);
                this.$store.commit("core/setGuidedProperties", {tourStarted: false});
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
                    this.source = this.flow.source;
                } else if (blueprintId && blueprintSource) {
                    this.source = await this.$store.dispatch("blueprints/getBlueprintSource", {type: blueprintSource, kind: "flow", id: blueprintId});
                } else {
                    const selectedNamespace = this.$route.query.namespace || "company.team";
                    this.source = `id: myflow
namespace: ${selectedNamespace}

tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello World! 🚀`;
                }
            }
        },
        computed: {
            sourceWrapper() {
                return {source: this.source};
            },
            ...mapState("flow", ["flowGraph"]),
            ...mapState("auth", ["user"]),
            ...mapState("plugin", ["pluginSingleList", "pluginsDocumentation"]),
            ...mapGetters("core", ["guidedProperties"]),
            ...mapGetters("flow", ["flow", "flowValidation"]),
            routeInfo() {
                return {
                    title: this.$t("flows")
                };
            },
            flowParsed() {
                return YamlUtils.parse(this.source);
            }
        },
        beforeRouteLeave(to, from, next) {
            this.$store.commit("flow/setFlow", null);
            next();
        }
    };
</script>
