<template>
    <TopNavBar :title="routeInfo.title" />
    <section class="full-container">
        <MultiPanelFlowEditorView v-if="flowStore.flow" />
    </section>
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount} from "vue";
    import {useRoute, onBeforeRouteLeave} from "vue-router";
    import {useI18n} from "vue-i18n";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import MultiPanelFlowEditorView from "./MultiPanelFlowEditorView.vue";
    import {useBlueprintsStore} from "../../stores/blueprints";
    import {useCoreStore} from "../../stores/core";
    import {getRandomID} from "../../../scripts/id";
    import {useEditorStore} from "../../stores/editor";
    import {useFlowStore} from "../../stores/flow";
    import {defaultNamespace} from "../../composables/useNamespaces";
    import {useVueTour} from "../../composables/useVueTour";

    const route = useRoute();
    const {t} = useI18n();

    const tour = useVueTour("guidedTour");

    const blueprintsStore = useBlueprintsStore();
    const coreStore = useCoreStore();
    const editorStore = useEditorStore();
    const flowStore = useFlowStore();

    const setupFlow = async () => {
        const blueprintId = route.query.blueprintId as string;
        const blueprintSource = route.query.blueprintSource as string;
        let flowYaml = "";
        const id = getRandomID();
        const selectedNamespace = (route.query.namespace as string) || defaultNamespace() || "company.team";

        if (route.query.copy && flowStore.flow) {
            flowYaml = flowStore.flow.source;
        } else if (blueprintId && blueprintSource) {
            flowYaml = await blueprintsStore.getBlueprintSource({
                type: blueprintSource,
                kind: "flow",
                id: blueprintId
            });
        } else {
            flowYaml = `
id: ${id}
namespace: ${selectedNamespace}

tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello World! 🚀`.trim();
        }

        flowStore.flow = {
            id,
            namespace: selectedNamespace,
            ...YAML_UTILS.parse(flowYaml),
            source: flowYaml,
        };

        flowStore.initYamlSource();
    };

    const routeInfo = computed(() => {
        return {
            title: t("flows")
        };
    });

    flowStore.isCreating = true;
    if (route.query.reset) {
        localStorage.setItem("tourDoneOrSkip", "");
        coreStore.guidedProperties = {
            ...coreStore.guidedProperties,
            tourStarted: true,
        };
        tour.start();
    }
    setupFlow();
    editorStore.closeAllTabs();

    onBeforeUnmount(() => {
        flowStore.flowValidation = undefined;
    });

    onBeforeRouteLeave(() => {
        flowStore.flow = undefined;
    });
</script>
