<template>
    <TopNavBar :title="routeInfo.title" />
    <section class="full-container">
        <MultiPanelFlowEditorView v-if="flowStore.flow" />
    </section>
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount} from "vue";
    import {useRoute} from "vue-router";
    import {useI18n} from "vue-i18n";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import MultiPanelFlowEditorView from "./MultiPanelFlowEditorView.vue";
    import {useBlueprintsStore} from "../../stores/blueprints";
    import {getRandomID} from "../../../scripts/id";
    import {useFlowStore} from "../../stores/flow";
    import {defaultNamespace} from "../../composables/useNamespaces";

    import type {BlueprintType} from "../../stores/blueprints"
    import {useAuthStore} from "../../override/stores/auth";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {useOnboardingV2Store} from "../../stores/onboardingV2";

    const route = useRoute();
    const {t} = useI18n();

    const blueprintsStore = useBlueprintsStore();
    const flowStore = useFlowStore();
    const authStore = useAuthStore();
    const onboardingV2Store = useOnboardingV2Store();

    const setupFlow = async () => {
        const blueprintId = route.query.blueprintId as string;
        const blueprintSource = route.query.blueprintSource as BlueprintType;
        const blueprintSourceYaml = route.query.blueprintSourceYaml as string;
        const isGuidedOnboarding = route.query.onboarding === "guided";
        const implicitDefaultNamespace = authStore.user?.getNamespacesForAction(
            permission.FLOW,
            action.CREATE,
        )[0];
        let flowYaml = "";
        const id = getRandomID();
        const selectedNamespace = (route.query.namespace as string)
            ?? defaultNamespace()
            ?? implicitDefaultNamespace
            ?? "company.team";

        if (route.query.copy && flowStore.flow) {
            flowYaml = flowStore.flow.source;
        } else if (blueprintId && blueprintSourceYaml) {
            flowYaml = blueprintSourceYaml;
        } else if(blueprintId && blueprintSource === "community"){
            flowYaml = await blueprintsStore.getBlueprintSource({
                type: blueprintSource,
                kind: "flow",
                id: blueprintId
            });
        } else if (blueprintId) {
            const flowBlueprint = await blueprintsStore.getFlowBlueprint(blueprintId);
            flowYaml = flowBlueprint.source;
        } else if (isGuidedOnboarding) {
            flowYaml = `# ${t("onboarding.editor_hints.build_intro")}\n`;
        } else {
            flowYaml = `
id: ${id}
namespace: ${selectedNamespace}

tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello World! 🚀`.trim();
        }

        let parsedFlow = {};
        try {
            parsedFlow = YAML_UTILS.parse(flowYaml) ?? {};
        } catch {
            parsedFlow = {};
        }

        flowStore.flow = {
            id,
            namespace: selectedNamespace,
            ...parsedFlow,
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
    if (route.query.reset || route.query.onboarding === "guided") {
        onboardingV2Store.startGuided();
    }
    setupFlow();

    onBeforeUnmount(() => {
        flowStore.flowValidation = undefined;
        flowStore.flow = undefined;
    });
</script>
