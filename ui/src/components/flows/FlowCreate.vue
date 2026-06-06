<template>
    <TopNavBar :title="routeInfo.title">
        <template #actions>
            <Actions />
        </template>
    </TopNavBar>
    <section class="full-container">
        <MultiPanelFlowEditorView v-if="flowStore.flow" />
    </section>
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
    import TopNavBar from "../../components/layout/TopNavBar.vue"
    import Actions from "override/components/flows/Actions.vue"
    import MultiPanelFlowEditorView from "./MultiPanelFlowEditorView.vue"
    import {useBlueprintsStore} from "../../stores/blueprints"
    import {getRandomID} from "../../utils/id"
    import {useFlowStore} from "../../stores/flow"
    import {defaultNamespace} from "../../composables/useNamespaces"
    import useRouteContext from "../../composables/useRouteContext"

    import type {BlueprintType} from "../../stores/blueprints"
    import {useAuthStore} from "override/stores/auth"
    import {useMiscStore} from "override/stores/misc"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import {useOnboardingV2Store} from "../../stores/onboardingV2"

    const route = useRoute()
    const {t} = useI18n()

    const blueprintsStore = useBlueprintsStore()
    const flowStore = useFlowStore()
    const authStore = useAuthStore()
    const onboardingV2Store = useOnboardingV2Store()
    const miscStore = useMiscStore()
    const ONBOARDING_FLOW_PRESET_KEY = "kestra.onboarding.flowPreset"

    const defaultFlowTemplate = (id: string, namespace: string) => {
        const configuredTemplate = miscStore.configs?.flowTemplate
        if (typeof configuredTemplate === "string" && configuredTemplate.trim()) {
            return configuredTemplate.trim()
        }

        return `
id: ${id}
namespace: ${namespace}

tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello World! 🚀`.trim()
    }

    const isRecord = (value: unknown): value is Record<string, unknown> => {
        return typeof value === "object" && value !== null && !Array.isArray(value)
    }

    const withGeneratedFlowMetadata = (source: string, parsedFlow: Record<string, unknown>, id: string, namespace: string) => {
        const metadata: string[] = []
        if (!("id" in parsedFlow)) {
            metadata.push(`id: ${id}`)
        }

        if (!("namespace" in parsedFlow)) {
            metadata.push(`namespace: ${namespace}`)
        }

        return metadata.length > 0 ? `${metadata.join("\n")}\n\n${source}`.trim() : source
    }

    const setupFlow = async () => {
        const blueprintId = route.query.blueprintId as string
        const blueprintSource = route.query.blueprintSource as BlueprintType
        const blueprintSourceYaml = route.query.blueprintSourceYaml as string
        const isGuidedOnboarding = route.query.onboarding === "guided"
        const onboardingPresetFlow = route.query.onboardingPreset === "true"
            ? sessionStorage.getItem(ONBOARDING_FLOW_PRESET_KEY) ?? ""
            : ""
        const implicitDefaultNamespace = authStore.user?.getNamespacesForAction(
            resource.FLOW,
            action.CREATE,
        )[0]
        let flowYaml = ""
        let shouldApplyGeneratedMetadata = false
        const id = getRandomID()
        const selectedNamespace = (route.query.namespace as string)
            ?? defaultNamespace()
            ?? implicitDefaultNamespace
            ?? "company.team"

        if (route.query.copy && flowStore.flow) {
            flowYaml = flowStore.flow.source
        } else if (onboardingPresetFlow) {
            flowYaml = onboardingPresetFlow
            sessionStorage.removeItem(ONBOARDING_FLOW_PRESET_KEY)
        } else if (blueprintId && blueprintSourceYaml) {
            flowYaml = blueprintSourceYaml
        } else if(blueprintId && blueprintSource === "community"){
            flowYaml = await blueprintsStore.getBlueprintSource({
                type: blueprintSource,
                kind: "flow",
                id: blueprintId,
            })
        } else if (blueprintId) {
            const flowBlueprint = await blueprintsStore.getFlowBlueprint(blueprintId)
            flowYaml = flowBlueprint.source
        } else if (isGuidedOnboarding) {
            flowYaml = `# ${t("onboarding.editor_hints.build_intro")}\n`
        } else {
            flowYaml = defaultFlowTemplate(id, selectedNamespace)
            shouldApplyGeneratedMetadata = true
        }

        let parsedFlow: Record<string, unknown> = {}
        try {
            const parsed = YAML_UTILS.parse(flowYaml)
            parsedFlow = isRecord(parsed) ? parsed : {}
        } catch {
            parsedFlow = {}
        }

        if (shouldApplyGeneratedMetadata) {
            flowYaml = withGeneratedFlowMetadata(flowYaml, parsedFlow, id, selectedNamespace)
        }

        flowStore.flow = {
            id,
            namespace: selectedNamespace,
            ...parsedFlow,
            source: flowYaml,
        }

        flowStore.initYamlSource()
    }

    const routeInfo = computed(() => {
        return {
            title: t("flows"),
        }
    })

    useRouteContext(routeInfo)

    flowStore.isCreating = true
    if (route.query.reset || route.query.onboarding === "guided") {
        onboardingV2Store.startGuided()
    }
    setupFlow()

    onBeforeUnmount(() => {
        flowStore.flowValidation = undefined
        flowStore.flow = undefined
    })
</script>
