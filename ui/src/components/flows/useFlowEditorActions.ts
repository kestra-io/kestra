import {computed} from "vue"
import {useRoute, useRouter} from "vue-router"

import * as localUtils from "../../utils/utils"
import {isSuccessfulFlowSaveOutcome, useFlowStore} from "../../stores/flow"
import {useExecutionsStore} from "../../stores/executions"
import {useOnboardingV2Store} from "../../stores/onboardingV2"
import {usePlaygroundStore} from "../../stores/playground"
import {useToast} from "../../utils/toast"

export function useFlowEditorActions() {
    const flowStore = useFlowStore()
    const executionsStore = useExecutionsStore()
    const onboardingStore = useOnboardingV2Store()
    const playgroundStore = usePlaygroundStore()
    const router = useRouter()
    const route = useRoute()
    const toast = useToast()

    const hasFlowSourceChange = computed(() => flowStore.haveChange)
    const haveChange = computed(() => hasFlowSourceChange.value || flowStore.hasDirtyEditorFiles)
    const canSave = computed(() => haveChange.value || flowStore.isCreating)
    const hasErrors = computed(() => (flowStore.flowErrors?.length ?? 0) > 0)
    const isReadOnly = computed(() => flowStore.isReadOnly)
    const isAllowedEdit = computed(() => flowStore.isAllowedEdit)
    const tenant = computed(() => route.params.tenant)

    async function flushDirtyFiles() {
        const cb = flowStore.filesSaveAll
        if (cb) {
            await cb()
        }
    }

    async function save() {
        try {
            // Save the isCreating before saving.
            // saveAll can change its value.
            const isCreating = flowStore.isCreating
            const outcome = await flowStore.saveAll()
            if (isSuccessfulFlowSaveOutcome(outcome)) {
                onboardingStore.recordSave()
            }

            if (isCreating && outcome === "redirect_to_update") {
                await router.push({
                    name: "flows/update",
                    params: {
                        id: flowStore.flow?.id,
                        namespace: flowStore.flow?.namespace,
                        tab: "edit",
                        tenant: tenant.value,
                    },
                    query: route.query,
                })
            }

            await flushDirtyFiles()
        } catch (error: any) {
            if (error?.status === 401) {
                toast.error("401 Unauthorized", undefined, {duration: 2000})
            }
        }
    }

    async function saveAndExecute() {
        try {
            const isCreating = flowStore.isCreating
            const outcome = await flowStore.saveAll()
            const hasInputs = Array.isArray(flowStore.flowParsed?.inputs)
                && flowStore.flowParsed.inputs.length > 0
            if (isSuccessfulFlowSaveOutcome(outcome)) {
                onboardingStore.recordSave()
            }

            if (
                isSuccessfulFlowSaveOutcome(outcome)
                && !hasInputs
                && flowStore.flow?.id
                && flowStore.flow?.namespace
            ) {
                const response = await executionsStore.triggerExecution({
                    namespace: flowStore.flow.namespace,
                    id: flowStore.flow.id,
                    formData: undefined,
                    kind: "NORMAL",
                    labels: ["system.from:ui"],
                })

                executionsStore.execution = response.data
                onboardingStore.recordExecution()

                await router.push({
                    name: "executions/update",
                    params: {
                        namespace: response.data.namespace,
                        flowId: response.data.flowId,
                        id: response.data.id,
                        tab: "gantt",
                        tenant: tenant.value,
                    },
                    query: {
                        autoExpandGantt: "true",
                        onboardingSuccess: "true",
                    },
                })

                await flushDirtyFiles()
                return
            }

            if (isCreating && outcome === "redirect_to_update") {
                await router.push({
                    name: "flows/update",
                    params: {
                        id: flowStore.flow?.id,
                        namespace: flowStore.flow?.namespace,
                        tab: "edit",
                        tenant: tenant.value,
                    },
                    query: route.query,
                })
            }

            if (isSuccessfulFlowSaveOutcome(outcome)) {
                window.setTimeout(() => {
                    flowStore.executeFlow = true
                }, 300)
            }

            await flushDirtyFiles()
        } catch (error: any) {
            if (error?.status === 401) {
                toast.error("401 Unauthorized", undefined, {duration: 2000})
            }
        }
    }

    function exportYaml() {
        if (!flowStore.flow || !flowStore.flowYaml) return
        const {id, namespace} = flowStore.flow
        const blob = new Blob([flowStore.flowYaml], {type: "text/yaml"})
        localUtils.downloadUrl(window.URL.createObjectURL(blob), `${namespace}.${id}.yaml`)
    }

    function copyFlow() {
        return router.push({
            name: "flows/create",
            query: {copy: "true"},
            params: {tenant: tenant.value},
        })
    }

    function deleteFlow() {
        const flowId = flowStore.flowYamlMetadata?.id
        return flowStore.deleteFlowAndDependencies()
            .then(() => {
                toast.deleted(flowId)
                return router.push({
                    name: "flows/list",
                    params: {tenant: tenant.value},
                })
            })
            .catch(() => {
                toast.error(`Failed to delete flow ${flowId}`)
            })
    }

    function togglePlayground() {
        playgroundStore.enabled = !playgroundStore.enabled
    }

    const isPlaygroundEnabled = computed(() => playgroundStore.enabled)
    const isPlaygroundAllowed = computed(
        () => localStorage.getItem("editorPlayground") !== "false"
            && !onboardingStore.isGuidedActive,
    )

    return {
        // state
        haveChange,
        hasFlowSourceChange,
        canSave,
        hasErrors,
        isReadOnly,
        isAllowedEdit,
        isPlaygroundEnabled,
        isPlaygroundAllowed,
        // actions
        save,
        saveAndExecute,
        exportYaml,
        copyFlow,
        deleteFlow,
        togglePlayground,
    }
}
