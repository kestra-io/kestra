<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="full-container">
        <MultiPanelFlowEditorView v-if="flowStore.flow" />
    </section>
</template>

<script>
    import {mapStores} from "pinia";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import MultiPanelFlowEditorView from "./MultiPanelFlowEditorView.vue";
    import {storageKeys} from "../../utils/constants";
    import {useBlueprintsStore} from "../../stores/blueprints";
    import {useCoreStore} from "../../stores/core";
    import {editorViewTypes} from "../../utils/constants";

    import {getRandomID} from "../../../scripts/id";
    import {useEditorStore} from "../../stores/editor";
    import {useFlowStore} from "../../stores/flow";

    export default {
        mixins: [RouteContext],
        components: {
            MultiPanelFlowEditorView,
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
                    flowYaml = `id: ${getRandomID()}
namespace: ${selectedNamespace}

tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello World! 🚀`;
                }

                this.flowStore.flowYaml = flowYaml;
                this.flowStore.flowYamlBeforeAdd = flowYaml;

                this.flowStore.flow = {...YAML_UTILS.parse(this.flowYaml), source: this.flowStore.flowYaml};
                this.flowStore.initYamlSource({viewTypes: editorViewTypes.SOURCE_DOC});
            }
        },
        computed: {
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
