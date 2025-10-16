<template>
    <TopNavBar :title="routeInfo.title" />
    <section class="full-container">
        <MultiPanelFlowEditorView v-if="flowStore.flow" />
    </section>
</template>

<script setup lang="ts">
    import {computed, onMounted, onBeforeUnmount} from "vue";
    import {useRoute, onBeforeRouteLeave} from "vue-router";
    import {useI18n} from "vue-i18n";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import MultiPanelFlowEditorView from "./MultiPanelFlowEditorView.vue";
    import {useBlueprintsStore} from "../../stores/blueprints";
    import {useCoreStore} from "../../stores/core";
    import {editorViewTypes} from "../../utils/constants";
    import {getRandomID} from "../../../scripts/id";
    import {useEditorStore} from "../../stores/editor";
    import {useFlowStore} from "../../stores/flow";
    import {defaultNamespace} from "../../composables/useNamespaces";
    import {useTour} from "vue-tour";

    const route = useRoute();
    const {t} = useI18n();
    const tour = useTour("guidedTour");

    const blueprintsStore = useBlueprintsStore();
    const coreStore = useCoreStore();
    const editorStore = useEditorStore();
    const flowStore = useFlowStore();

    const setupFlow = async () => {
        const blueprintId = route.query.blueprintId as string;
        const blueprintSource = route.query.blueprintSource as string;
        let flowYaml = "";

        if (route.query.copy && flowStore.flow) {
            flowYaml = flowStore.flow.source;
        } else if (blueprintId && blueprintSource) {
            flowYaml = await blueprintsStore.getBlueprintSource({
                type: blueprintSource,
                kind: "flow",
                id: blueprintId
            });
        } else {
            const selectedNamespace = (route.query.namespace as string) || defaultNamespace() || "company.team";
            flowYaml = `id: ${getRandomID()}\nnamespace: ${selectedNamespace}\n\ntasks:\n  - id: hello\n    type: io.kestra.plugin.core.log.Log\n    message: Hello World! 🚀`;
        }

        flowStore.flowYaml = flowYaml;
        flowStore.flowYamlBeforeAdd = flowYaml;

        flowStore.flow = {...YAML_UTILS.parse(flowStore.flowYaml), source: flowStore.flowYaml};
        flowStore.initYamlSource({viewTypes: editorViewTypes.SOURCE_DOC});
    };

    const routeInfo = computed(() => {
        return {
            title: t("flows")
        };
    });

    onMounted(() => {
        flowStore.isCreating = true;
        if (route.query.reset) {
            localStorage.setItem("tourDoneOrSkip", "");
            coreStore.guidedProperties = {...coreStore.guidedProperties, tourStarted: true};
            tour.start();
        }
        setupFlow();
        editorStore.closeAllTabs();
    });

    onBeforeUnmount(() => {
        flowStore.flowValidation = undefined;
    });

    onBeforeRouteLeave(() => {
        flowStore.flow = undefined;
    });
</script>
