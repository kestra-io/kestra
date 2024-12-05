<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="full-container">
        <editor-view
            v-if="source"
            :flow-id="flowParsed?.id"
            :namespace="flowParsed?.namespace"
            :is-creating="true"
            :flow-graph="flowGraph"
            :is-read-only="false"
            :is-dirty="true"
            :flow="sourceWrapper"
            :next-revision="1"
        />
    </section>
</template>

<script>
    import EditorView from "../inputs/EditorView.vue";
    import {mapGetters, mapMutations, mapState} from "vuex";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import {apiUrl} from "override/utils/route";
    import {YamlUtils} from "@kestra-io/ui-libs";

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

            async queryBlueprint(blueprintId) {
                return (await this.$http.get(`${this.blueprintUri}/${blueprintId}/flow`)).data;
            },
            async setupFlow() {
                if (this.$route.query.copy && this.flow){
                    this.source = this.flow.source;
                } else if (this.$route.query.blueprintId && this.$route.query.blueprintSource) {
                    this.source = await this.queryBlueprint(this.$route.query.blueprintId)
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
            ...mapState("flow", ["flowGraph", "total"]),
            ...mapState("auth", ["user"]),
            ...mapState("plugin", ["pluginSingleList", "pluginsDocumentation"]),
            ...mapGetters("core", ["guidedProperties"]),
            ...mapGetters("flow", ["flow", "flowValidation"]),
            routeInfo() {
                return {
                    title: this.$t("flows")
                };
            },
            blueprintUri() {
                return `${apiUrl(this.$store)}/blueprints/${this.$route.query.blueprintSource}`
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
