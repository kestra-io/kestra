<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="full-container">
        <MultiPanelEditorView v-if="flowStore.flow" />
    </section>
</template>

<script>
    import {mapState} from "vuex";
    import {mapStores} from "pinia";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import MultiPanelEditorView from "./MultiPanelEditorView.vue";
    import {storageKeys} from "../../utils/constants";
    import {useBlueprintsStore} from "../../stores/blueprints";
    import {useCoreStore} from "../../stores/core";

    import {getRandomFlowID} from "../../../scripts/product/flow";
    import {useEditorStore} from "../../stores/editor";
    import {useFlowStore} from "../../stores/flow";

    export default {
        mixins: [RouteContext],
        components: {
            MultiPanelEditorView,
            TopNavBar
        },

        created() {
            this.flowStore.isCreating = true;
            if (this.$route.query.reset) {
                localStorage.setItem("tourDoneOrSkip", undefined);
                this.coreStore.guidedProperties = {...this.coreStore.guidedProperties, tourStarted: true};
                this.$tours["guidedTour"]?.start();
            }
            this.setupFlow()
            this.editorStore.closeAllTabs()
        },
        beforeUnmount() {
            this.flowStore.flowValidation = undefined;
        },
        methods: {
            async setupFlow() {
                const blueprintId = this.$route.query.blueprintId;
                const blueprintSource = this.$route.query.blueprintSource;
                let flowYaml = ""
                if (this.$route.query.copy && this.flowStore.flow){
                    flowYaml = this.flowStore.flow.source;
                } else if (blueprintId && blueprintSource) {
                    flowYaml = await this.blueprintsStore.getBlueprintSource({type: blueprintSource, kind: "flow", id: blueprintId});
                } else {
                    const defaultNamespace = localStorage.getItem(storageKeys.DEFAULT_NAMESPACE);
                    const selectedNamespace = this.$route.query.namespace || defaultNamespace || "company.team";
                    flowYaml = `id: ${getRandomFlowID()}
namespace: ${selectedNamespace}

tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello World! 🚀`;
                }

                this.flowStore.flowYaml = flowYaml;
                this.flowStore.flowYamlBeforeAdd = flowYaml;

                this.flowStore.flow = {...YAML_UTILS.parse(this.flowYaml), source: this.flowStore.flowYaml};
                this.flowStore.initYamlSource();
            }
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapStores(useBlueprintsStore, useCoreStore, useEditorStore, useFlowStore),
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
            this.flowStore.flow = undefined;
            next();
        }
    };
</script>
