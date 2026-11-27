import {computed, h, nextTick} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useI18n} from "vue-i18n"

import * as localUtils from "../../utils/utils"
import {isSuccessfulFlowSaveOutcome, useFlowStore} from "../../stores/flow"
import {useExecutionsStore} from "../../stores/executions"
import {usePlaygroundStore} from "../../stores/playground"
import {useFlowAutosaveStore} from "../../stores/flowAutosave"
import {useFlowSaveStatus} from "./useFlowSaveStatus"
import {usePluginsStore} from "../../stores/plugins"
import {useMiscStore} from "override/stores/misc"
import {useCoreStore} from "../../stores/core"
import {useToast} from "../../utils/toast"
import {KsNotification} from "@kestra-io/design-system"
import {asProblem} from "@kestra-io/kestra-sdk"
import type {KestraHttpError} from "../../utils/kestraHttp"
import PluginInstallToast from "../plugins/PluginInstallToast.vue"

export function useFlowEditorActions() {
    const flowStore = useFlowStore()
    const executionsStore = useExecutionsStore()
    const playgroundStore = usePlaygroundStore()
    const pluginsStore = usePluginsStore()
    const router = useRouter()
    const route = useRoute()
    const toast = useToast()
    const {t} = useI18n()

    const hasFlowSourceChange = computed(() => flowStore.haveChange)
    const haveChange = computed(() => hasFlowSourceChange.value || flowStore.hasDirtyEditorFiles)
    const canSave = computed(() => haveChange.value || flowStore.isCreating)
    const {canPublish, hasUnpublishedChanges, state: saveState, errorCount} = useFlowSaveStatus()
    const executeBlockedReason = computed(() => {
        if (!hasUnpublishedChanges.value) return undefined
        return saveState.value === "invalid"
            ? t("flowSaveStatus.executeBlockedInvalid", {count: errorCount.value}, errorCount.value)
            : t("flowSaveStatus.executeBlocked")
    })
    const publishHighlighted = computed(() => useFlowAutosaveStore().publishHighlighted)
    const pointToPublish = () => useFlowAutosaveStore().pointToPublish()
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

    async function persistAll(draft: boolean) {
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
                query: {
                    ...route.query,
                    playground: playgroundStore.panelOpen ? "on" : undefined,
                },
            })
        }

        await flushDirtyFiles()
    }

    // Upper bound on how long a save waits on a plugin install job before giving up.
    // PluginInstallToast keeps polling and updating in the background past this point — this only
    // stops the save button from hanging forever on a hung/slow download.
    const INSTALL_WAIT_TIMEOUT_MS = 60_000

    type PluginInstallOutcome = "none" | "installed" | "failed" | "timeout"

    /**
     * Detects missing plugins for the current flow YAML and, if any are found, enqueues an
     * installation job and opens a live-progress notification toast. Resolves with the install
     * outcome once it reaches a terminal state or the wait times out, so callers can skip the
     * save when the plugin never got installed (it would only fail again as an unknown type).
     * The feature flag comes from the instance configs — no request is even made when it is off.
     * Polling is owned solely by {@link PluginInstallToast} (single poll loop, reused here via
     * its success/failure callbacks). Concurrent calls (held-down Ctrl+S, double-clicked Save)
     * join the in-flight cycle instead of stacking duplicate jobs and toasts.
     */
    let pluginInstallInFlight: Promise<PluginInstallOutcome> | null = null

    function triggerPluginInstallIfNeeded(): Promise<PluginInstallOutcome> {
        if (pluginInstallInFlight) return pluginInstallInFlight
        pluginInstallInFlight = doTriggerPluginInstall().finally(() => {
            pluginInstallInFlight = null
        })
        return pluginInstallInFlight
    }

    async function doTriggerPluginInstall(): Promise<PluginInstallOutcome> {
        // Global kill switch from the instance configs: when the feature is off, no auto-install
        // request is made at all.
        if (useMiscStore().configs?.isPluginAutoInstallEnabled !== true) return "none"

        const yaml = flowStore.flowYaml
        if (!yaml) return "none"

        let detection
        try {
            detection = await pluginsStore.detectMissingPlugins(yaml)
        } catch {
            return "none"
        }

        if (!detection.enabled || detection.artifacts.length === 0) return "none"

        // The editor's eager graph regeneration raises a persistent invalid-type error toast for
        // the very types this install is about to provide — dismiss it only when it names one of
        // them, so unrelated errors keep showing when auto-install is off or not involved.
        const coreStore = useCoreStore()
        const pendingErrorContent = coreStore.message?.content ? JSON.stringify(coreStore.message.content) : ""
        if (detection.missingTypes.some((type) => pendingErrorContent.includes(type))) {
            coreStore.message = undefined
        }

        let job
        try {
            job = await pluginsStore.startInstall(detection.artifacts)
        } catch {
            toast.error(t("plugins.autoInstall.failed"))
            return "failed"
        }

        const count = detection.artifacts.length
        let notificationHandle: ReturnType<typeof KsNotification> | undefined

        return await new Promise<PluginInstallOutcome>((resolve) => {
            let settled = false
            const settle = (outcome: PluginInstallOutcome) => {
                if (settled) return
                settled = true
                resolve(outcome)
            }

            notificationHandle = KsNotification({
                title: t("plugins.autoInstall.title", count),
                message: h(PluginInstallToast, {
                    jobId: job.id ?? "",
                    onSuccess: () => {
                        pluginsStore.list()
                        setTimeout(() => notificationHandle?.close(), 3000)
                        settle("installed")
                    },
                    // The toast itself explains the failure and that the flow was not saved.
                    onFailure: () => settle("failed"),
                }),
                position: "bottom-right",
                type: "info",
                duration: 0,
                customClass: "kel-notification__large",
            })

            setTimeout(() => {
                if (settled) return
                toast.warning(t("plugins.autoInstall.timeout"))
                settle("timeout")
            }, INSTALL_WAIT_TIMEOUT_MS)
        })
    }

    function reportSaveError(error: unknown) {
        const httpError = error as KestraHttpError
        if (httpError?.status === 401) {
            toast.error(t("errors.401.content"), t("errors.401.title"), {duration: 2000})
        } else {
            // A validation error and a lost connection reach here unreported; anything the
            // interceptor toasted, a 404 on a flow deleted under the editor included, is on screen
            // already with its problem title and its own remedies.
            toast.requestError(error, asProblem(error)?.detail ?? t("error"))
        }
    }

    /** The Publish button: pushes the pending autosave first, so the publish is the last write. */
    async function publish() {
        const autosave = useFlowAutosaveStore()
        await autosave.settle()
        await saveFlow(false)
        const flow = flowStore.flow
        if (flow?.revision && !flow.draft && !flowStore.haveChange) autosave.notePublished(flow.revision)
    }

    /** The Ctrl/Cmd+S shortcut: creates a new flow as a draft, otherwise pushes the pending autosave now. */
    async function saveAsDraft() {
        if (flowStore.isCreating) {
            await saveFlow(true)
            return
        }
        try {
            const autosave = useFlowAutosaveStore()
            // The last form edit can reach the YAML a tick after the shortcut fires.
            await nextTick()
            await autosave.sync()
            await flushDirtyFiles()
            if (autosave.status === "idle" && flowStore.flow?.draft) {
                toast.success(t("saved as draft done", {name: flowStore.flow.id}), t("saved as draft"))
            }
        } catch (error) {
            reportSaveError(error)
        }
    }

    async function saveFlow(draft: boolean) {
        try {
            // Skip the save when the install did not succeed: it would only fail downstream as an
            // unknown type, burying the real cause under a second, unrelated-looking error.
            const outcome = await triggerPluginInstallIfNeeded()
            if (outcome === "failed" || outcome === "timeout") return
            await persistAll(draft)
        } catch (error) {
            reportSaveError(error)
        }
    }

    /** The Create button: creates the flow as a draft, which fixes its id and hands it to autosave. */
    async function createDraft() {
        try {
            await persistAll(true)
        } catch (error) {
            reportSaveError(error)
        }
    }

    async function publishDraft() {
        try {
            await flowStore.publishDraft()
            await flushDirtyFiles()
        } catch (error) {
            if ((error as KestraHttpError)?.status === 401) {
                toast.error(t("errors.401.content"), t("errors.401.title"), {duration: 2000})
            }
        }
    }

    async function saveAndExecute() {
        try {
            const installOutcome = await triggerPluginInstallIfNeeded()
            if (installOutcome === "failed" || installOutcome === "timeout") return

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
                    query: {
                        ...route.query,
                        playground: playgroundStore.panelOpen ? "on" : undefined,
                    },
                })
            }

            if (isSuccessfulFlowSaveOutcome(outcome)) {
                window.setTimeout(() => {
                    flowStore.executeFlow = true
                }, 300)
            }

            await flushDirtyFiles()
        } catch (error) {
            reportSaveError(error)
        }
    }

    function exportYaml() {
        if (!flowStore.flow || !flowStore.flowYaml) return
        const {id, namespace} = flowStore.flow
        localUtils.downloadBlob(new Blob([flowStore.flowYaml], {type: "text/yaml"}), `${namespace}.${id}.yaml`)
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
        return flowStore.deleteFlowAndDependencies(router)
            .then(() => {
                toast.deleted(flowId ?? "")
                return router.push({
                    name: "flows/list",
                    params: {tenant: tenant.value},
                })
            })
            .catch((error: unknown) => {
                toast.requestError(error, t("delete flow error", {id: flowId}))
            })
    }

    return {
        // state
        haveChange,
        hasFlowSourceChange,
        canSave,
        canPublish,
        executeBlockedReason,
        publishHighlighted,
        pointToPublish,
        hasErrors,
        isReadOnly,
        isAllowedEdit,
        isDraft,
        // actions
        publish,
        /** @deprecated since 2.1, use `publish` instead. */
        save: publish,
        saveAsDraft,
        createDraft,
        publishDraft,
        saveAndExecute,
        exportYaml,
        copyFlow,
        deleteFlow,
    }
}
