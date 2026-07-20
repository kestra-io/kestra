import {computed, h} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useI18n} from "vue-i18n"

import * as localUtils from "../../utils/utils"
import {isSuccessfulFlowSaveOutcome, useFlowStore} from "../../stores/flow"
import {useExecutionsStore} from "../../stores/executions"
import {useProductTourStore} from "../../stores/productTour"
import {usePlaygroundStore} from "../../stores/playground"
import {usePluginsStore} from "../../stores/plugins"
import {useToast} from "../../utils/toast"
import {KsNotification} from "@kestra-io/design-system"
import PluginInstallToast from "../plugins/PluginInstallToast.vue"

export function useFlowEditorActions() {
    const flowStore = useFlowStore()
    const executionsStore = useExecutionsStore()
    const tourStore = useProductTourStore()
    const playgroundStore = usePlaygroundStore()
    const pluginsStore = usePluginsStore()
    const router = useRouter()
    const route = useRoute()
    const toast = useToast()
    const {t} = useI18n()

    const hasFlowSourceChange = computed(() => flowStore.haveChange)
    const haveChange = computed(() => hasFlowSourceChange.value || flowStore.hasDirtyEditorFiles)
    const canSave = computed(() => haveChange.value || flowStore.isCreating)
    const hasErrors = computed(() => (flowStore.flowErrors?.length ?? 0) > 0)
    const isReadOnly = computed(() => flowStore.isReadOnly)
    const isAllowedEdit = computed(() => flowStore.isAllowedEdit)
    const isDraft = computed(() => flowStore.flow?.draft ?? false)
    const tenant = computed(() => route.params.tenant)

    async function flushDirtyFiles() {
        const cb = flowStore.filesSaveAll
        if (cb) {
            await cb()
        }
    }

    async function persistAll(draft?: boolean) {
        const isCreating = flowStore.isCreating
        const outcome = await flowStore.saveAll(draft)

        if (isCreating && outcome === "redirect_to_update") {
            await router.push({
                name: "flows/update/edit",
                params: {
                    id: flowStore.flow?.id,
                    namespace: flowStore.flow?.namespace,
                    tenant: tenant.value,
                },
                query: route.query,
            })
        }

        await flushDirtyFiles()
    }

    // Upper bound on how long a save waits on a plugin install job before giving up and saving
    // anyway. PluginInstallToast keeps polling and updating in the background past this point —
    // this only stops the save button from hanging forever on a hung/slow download.
    const INSTALL_WAIT_TIMEOUT_MS = 60_000

    /**
     * Detects missing plugins for the current flow YAML and, if any are found, enqueues an
     * installation job and opens a live-progress notification toast. Resolves once the install
     * reaches a terminal state, the wait times out, or immediately if nothing is missing — so
     * callers can await it before saving. The type the flow references must be registered before
     * the backend re-validates it, otherwise the save is rejected as an unknown type; polling for
     * that terminal state is owned solely by {@link PluginInstallToast} (single poll loop, reused
     * here via its success/failure callbacks) rather than duplicated with a second timer.
     */
    async function triggerPluginInstallIfNeeded(): Promise<void> {
        const yaml = flowStore.flowYaml
        if (!yaml) return

        let detection
        try {
            detection = await pluginsStore.detectMissingPlugins(yaml)
        } catch {
            return
        }

        if (!detection.enabled || detection.artifacts.length === 0) return

        let job
        try {
            job = await pluginsStore.startInstall(detection.artifacts)
        } catch {
            toast.error(t("plugins.autoInstall.failed"))
            return
        }

        const count = detection.artifacts.length
        let notificationHandle: ReturnType<typeof KsNotification> | undefined

        await new Promise<void>((resolve) => {
            let settled = false
            const settle = () => {
                if (settled) return
                settled = true
                resolve()
            }

            notificationHandle = KsNotification({
                title: t("plugins.autoInstall.title", count),
                message: h(PluginInstallToast, {
                    jobId: job.id,
                    onSuccess: () => {
                        pluginsStore.list()
                        setTimeout(() => notificationHandle?.close(), 3000)
                        settle()
                    },
                    onFailure: settle,
                }),
                position: "bottom-right",
                type: "info",
                duration: 0,
            })

            setTimeout(() => {
                if (settled) return
                toast.warning(t("plugins.autoInstall.timeout"))
                settle()
            }, INSTALL_WAIT_TIMEOUT_MS)
        })
    }

    function reportSaveError(error: any) {
        if (error?.status === 401) {
            toast.error("401 Unauthorized", undefined, {duration: 2000})
        } else {
            toast.error(error?.response?.data?.message ?? t("error"))
        }
    }

    async function save() {
        try {
            await triggerPluginInstallIfNeeded()
            await persistAll(false)
        } catch (error: any) {
            reportSaveError(error)
        }
    }

    async function saveAsDraft() {
        try {
            await persistAll(true)
        } catch (error: any) {
            reportSaveError(error)
        }
    }

    async function publishDraft() {
        try {
            await flowStore.publishDraft()
            await flushDirtyFiles()
        } catch (error: any) {
            if (error?.status === 401) {
                toast.error("401 Unauthorized", undefined, {duration: 2000})
            }
        }
    }

    async function saveAndExecute() {
        try {
            await triggerPluginInstallIfNeeded()

            const isCreating = flowStore.isCreating
            const outcome = await flowStore.saveAll()
            const hasInputs = Array.isArray(flowStore.flowParsed?.inputs)
                && flowStore.flowParsed.inputs.length > 0

            if (
                isSuccessfulFlowSaveOutcome(outcome)
                && !hasInputs
                && flowStore.flow?.id
                && flowStore.flow?.namespace
            ) {
                const response = await executionsStore.triggerExecution({
                    namespace: flowStore.flow.namespace,
                    id: flowStore.flow.id,
                    // Run the revision we just saved - except drafts, which are playground-only:
                    // omit the revision so the backend runs the latest published one.
                    revision: flowStore.flow.draft ? undefined : flowStore.flow.revision,
                    formData: undefined,
                    kind: "NORMAL",
                    labels: ["system.from:ui"],
                })

                executionsStore.execution = response

                await router.push({
                    name: "executions/update/gantt",
                    params: {
                        namespace: response.namespace,
                        flowId: response.flowId,
                        id: response.id,
                        tenant: tenant.value,
                    },
                    query: {
                        autoExpandGantt: "true",
                    },
                })

                await flushDirtyFiles()
                return
            }

            if (isCreating && outcome === "redirect_to_update") {
                await router.push({
                    name: "flows/update/edit",
                    params: {
                        id: flowStore.flow?.id,
                        namespace: flowStore.flow?.namespace,
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
            reportSaveError(error)
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
            && !tourStore.isGuidedActive,
    )

    return {
        // state
        haveChange,
        hasFlowSourceChange,
        canSave,
        hasErrors,
        isReadOnly,
        isAllowedEdit,
        isDraft,
        isPlaygroundEnabled,
        isPlaygroundAllowed,
        // actions
        save,
        saveAsDraft,
        publishDraft,
        saveAndExecute,
        exportYaml,
        copyFlow,
        deleteFlow,
        togglePlayground,
    }
}
