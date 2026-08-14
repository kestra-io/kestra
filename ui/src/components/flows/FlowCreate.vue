<template>
    <TopNavBar :title="routeInfo.title">
        <template #actions>
            <Actions />
        </template>
    </TopNavBar>
    <section class="full-container flush-top">
        <template v-if="showLanding">
            <ImportYaml
                v-if="showImport"
                @submit="handleImportSubmit"
                @back="showImport = false"
            />
            <NewFlowLanding
                v-else
                @proceed="handleLandingProceed"
                @import="showImport = true"
            />
        </template>
        <div v-else-if="setupError" class="flow-create-error" data-test="flow-create-error">
            <KsAlert
                type="error"
                :closable="false"
                :title="$t('something_went_wrong.opening_flow_editor')"
            >
                {{ setupError }}
            </KsAlert>
            <KsButton size="small" data-test="flow-create-error-retry" @click="retrySetup">
                {{ $t("something_went_wrong.retry") }}
            </KsButton>
        </div>
        <MultiPanelFlowEditorView v-else-if="flowStore.flow" />
    </section>
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount, ref} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
    import TopNavBar from "../../components/layout/TopNavBar.vue"
    import Actions from "override/components/flows/Actions.vue"
    import MultiPanelFlowEditorView from "./MultiPanelFlowEditorView.vue"
    import NewFlowLanding from "./create/NewFlowLanding.vue"
    import ImportYaml from "./create/ImportYaml.vue"
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
    import {ONBOARDING_FLOW_PRESET_KEY, RECIPE_PRESET_KEY} from "../../utils/storageKeys"
    import {shouldShowLanding} from "../../utils/flowCreationLanding"

    const route = useRoute()
    const {t} = useI18n()

    const blueprintsStore = useBlueprintsStore()
    const flowStore = useFlowStore()
    const authStore = useAuthStore()
    const miscStore = useMiscStore()

    const showLanding = ref(shouldShowLanding(route.query))
    const showImport = ref(false)

    const setupError = ref<string>()

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

    const setupFlow = async (overrideId?: string, overrideNamespace?: string, importYaml?: string) => {
        const blueprintId = route.query.blueprintId as string
        const blueprintSource = route.query.blueprintSource as BlueprintType
        const blueprintSourceYaml = route.query.blueprintSourceYaml as string
        const onboardingPresetFlow = route.query.onboardingPreset === "true"
            ? sessionStorage.getItem(ONBOARDING_FLOW_PRESET_KEY) ?? ""
            : ""
        const recipePresetFlow = route.query.recipePreset === "true"
            ? sessionStorage.getItem(RECIPE_PRESET_KEY) ?? ""
            : ""
        const implicitDefaultNamespace = authStore.user?.getNamespacesForAction(
            resource.FLOW,
            action.CREATE,
        )[0]
        let flowYaml = ""
        let shouldApplyGeneratedMetadata = false
        const id = overrideId ?? getRandomID()
        const selectedNamespace = overrideNamespace
            ?? (route.query.namespace as string)
            ?? defaultNamespace()
            ?? implicitDefaultNamespace
            ?? "company.team"

        if (route.query.copy && flowStore.flow) {
            flowYaml = flowStore.flow.source
        } else if (importYaml) {
            flowYaml = importYaml
            shouldApplyGeneratedMetadata = true
        } else if (recipePresetFlow) {
            flowYaml = recipePresetFlow
            sessionStorage.removeItem(RECIPE_PRESET_KEY)
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
            if (flowBlueprint.source) {
                flowYaml = flowBlueprint.source
            }
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

    let lastSetupArgs: Parameters<typeof setupFlow> = []

    /*
     * `setupFlow` used to be called without awaiting it or catching it. A rejection was
     * therefore an unhandled promise rejection, and the page stayed empty for good: the
     * editor is gated on `flowStore.flow` and the Save button on `isAllowedEdit`, which is
     * false while there is no flow. Awaiting it here turns that silent blank page into an
     * error the user (and the E2E suite) can see, with a way to try again.
     */
    const initialize = async (...args: Parameters<typeof setupFlow>) => {
        lastSetupArgs = args
        setupError.value = undefined

        try {
            await setupFlow(...args)
        } catch (error) {
            setupError.value = error instanceof Error ? error.message : String(error)
            console.error("Cannot open the flow creation editor.", error)
        }
    }

    // Retry has to replay the funnel choice, or an import would silently become a blank flow.
    const retrySetup = () => initialize(...lastSetupArgs)

    const handleLandingProceed = async ({id, namespace}: {id: string; namespace: string}) => {
        await initialize(id, namespace)
        showLanding.value = false
    }

    const handleImportSubmit = async ({yaml}: {yaml: string}) => {
        await initialize(undefined, undefined, yaml)
        showImport.value = false
        showLanding.value = false
    }

    const routeInfo = computed(() => {
        return {
            title: t("flows"),
        }
    })

    useRouteContext(routeInfo)

    flowStore.isCreating = true
    if (!showLanding.value) {
        initialize()
    }

    onBeforeUnmount(() => {
        flowStore.flowValidation = undefined
        flowStore.flow = undefined
    })
</script>

<style scoped>
    .flow-create-error {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-5);
    }
</style>
