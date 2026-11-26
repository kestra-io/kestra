import {computed, nextTick, ref, watch} from "vue"
import {defineStore} from "pinia"
import {useRoute} from "vue-router"
import {KsMessageBox} from "@kestra-io/design-system"
import {globalI18n} from "../translations/i18n"
import {useFlowStore, type Flow, type FlowValidations} from "./flow"
import {useServerReachability} from "../composables/useServerReachability"
import {handled, type KestraHttpError} from "../utils/kestraHttp"

export const AUTOSAVE_DELAY = 1_000
export const OFFLINE_RETRY_DELAY = 10_000
export const PUBLISHED_NOTICE_DURATION = 2_500

export type AutosaveStatus = "idle" | "pending" | "syncing" | "offline" | "conflict"

interface AutosaveBuffer {
    source: string
    /** Head source on the server when the local edits started, to tell a concurrent change from our own. */
    base: string
}

export interface AutosaveConflict {
    local: string
    server: Flow
}

const STORAGE_PREFIX = "kestra.flowAutosave."

/** Java's `String.hashCode()`, which the draft endpoint compares against the head source. */
export function sourceHash(source: string): number {
    let hash = 0
    for (let i = 0; i < source.length; i++) {
        hash = (Math.imul(31, hash) + source.charCodeAt(i)) | 0
    }
    return hash
}

export const useFlowAutosaveStore = defineStore("flowAutosave", () => {
    const flowStore = useFlowStore()
    const {unreachable} = useServerReachability()
    const route = useRoute()

    const status = ref<AutosaveStatus>("idle")
    const conflict = ref<AutosaveConflict>()
    const justPublished = ref<number>()
    const publishHighlighted = ref(false)

    let debounce: ReturnType<typeof setTimeout> | undefined
    let retry: ReturnType<typeof setTimeout> | undefined
    let inFlight: Promise<FlowValidations> | undefined
    let waiters: ((validation: FlowValidations) => void)[] = []
    let stopWatchers: (() => void)[] = []
    let newFlowRestoreOffered = false
    let publishedNotice: ReturnType<typeof setTimeout> | undefined
    let publishHighlight: ReturnType<typeof setTimeout> | undefined

    const bufferKey = computed(() => {
        const flow = flowStore.flow
        if (!flow?.id || flowStore.isCreating) return undefined
        return `${STORAGE_PREFIX}${route?.params?.tenant ?? ""}.${flow.namespace}.${flow.id}`
    })

    // A flow being created has no id yet, so its buffer is keyed by tenant until Create gives it one.
    const newFlowKey = computed(() => `${STORAGE_PREFIX}new.${route?.params?.tenant ?? ""}`)

    function t(key: string) {
        return globalI18n.value?.t(key) ?? key
    }

    async function offerNewFlowRestore() {
        const buffered = readBuffer(newFlowKey.value)?.source
        if (buffered && buffered !== flowStore.flowYaml && await confirmRestore()) {
            flowStore.flowYaml = buffered
        }
        newFlowRestoreOffered = true
    }

    function onCreatingEdit(source: string) {
        if (newFlowRestoreOffered) writeBuffer({source, base: ""}, newFlowKey.value)
    }

    function confirmRestore(): Promise<boolean> {
        return KsMessageBox({
            title: t("flowAutosave.restoreNew.title"),
            message: t("flowAutosave.restoreNew.message"),
            showCancelButton: true,
            confirmButtonText: t("flowAutosave.restoreNew.restore"),
            cancelButtonText: t("flowAutosave.restoreNew.discard"),
            distinguishCancelAndClose: true,
        }).then(() => true, () => false)
    }

    const canAutosave = computed(() =>
        bufferKey.value !== undefined
        && !flowStore.isReadOnly
        && flowStore.previewSource === undefined,
    )

    function readBuffer(key = bufferKey.value): AutosaveBuffer | undefined {
        if (!key) return undefined
        try {
            const raw = localStorage.getItem(key)
            return raw ? JSON.parse(raw) as AutosaveBuffer : undefined
        } catch {
            return undefined
        }
    }

    function writeBuffer(buffer: AutosaveBuffer, key = bufferKey.value) {
        if (!key) return
        try {
            localStorage.setItem(key, JSON.stringify(buffer))
        } catch {
            // Storage full or blocked: the debounce still pushes the change while online.
        }
    }

    function clearBuffer(key = bufferKey.value) {
        if (!key) return
        try {
            localStorage.removeItem(key)
        } catch {
            // Nothing to clean up when storage is blocked.
        }
    }

    function clearTimers() {
        clearTimeout(debounce)
        clearTimeout(retry)
        debounce = undefined
        retry = undefined
    }

    function currentValidation(): FlowValidations {
        return flowStore.flowValidation ?? {}
    }

    function settleWaiters(validation: FlowValidations) {
        const pending = waiters
        waiters = []
        pending.forEach(resolve => resolve(validation))
    }

    function schedule() {
        clearTimeout(debounce)
        debounce = setTimeout(() => sync().catch(() => undefined), AUTOSAVE_DELAY)
    }

    function onEdit(source: string) {
        if (flowStore.isCreating) {
            onCreatingEdit(source)
            return
        }
        if (!canAutosave.value || status.value === "conflict") return
        if (source === flowStore.flowYamlOrigin) {
            clearTimers()
            clearBuffer()
            status.value = "idle"
            settleWaiters(currentValidation())
            return
        }
        writeBuffer({source, base: readBuffer()?.base ?? flowStore.flow?.source ?? flowStore.flowYamlOrigin})
        status.value = unreachable.value ? "offline" : "pending"
        schedule()
    }

    /** The editor's validation request while autosave runs: the draft save answers it. */
    function validateBySaving(source: string): Promise<FlowValidations> | undefined {
        if (!canAutosave.value) return undefined
        if (status.value === "conflict") return Promise.resolve(currentValidation())
        if (readBuffer()?.source !== source) onEdit(source)
        return new Promise(resolve => waiters.push(resolve))
    }

    function sync(): Promise<FlowValidations> {
        clearTimers()
        inFlight ??= doSync()
            .then(validation => {
                settleWaiters(validation)
                return validation
            }, error => {
                settleWaiters(currentValidation())
                throw error
            })
            .finally(() => {
                inFlight = undefined
            })
        return inFlight
    }

    async function doSync(): Promise<FlowValidations> {
        const key = bufferKey.value
        const buffer = readBuffer(key)
        if (!key || !buffer || !flowStore.flow) return currentValidation()

        status.value = "syncing"
        try {
            const result = await flowStore.saveDraft({source: buffer.source, baseSourceHash: sourceHash(buffer.base)})
            const validation = (result.validation ?? {}) as FlowValidations
            if (result.conflict && result.flow) {
                conflict.value = {local: buffer.source, server: result.flow as Flow}
                status.value = "conflict"
                return validation
            }
            onSynced(key, buffer.source, result.flow?.source ?? buffer.source)
            return validation
        } catch (error) {
            if ((error as KestraHttpError)?.status !== undefined && !unreachable.value) {
                status.value = "pending"
                throw error
            }
            handled(error)
            status.value = "offline"
            retry = setTimeout(() => sync().catch(() => undefined), OFFLINE_RETRY_DELAY)
            return currentValidation()
        }
    }

    function onSynced(key: string, source: string, serverSource: string) {
        const current = readBuffer(key)
        if (!current || current.source === source) {
            clearBuffer(key)
        } else {
            writeBuffer({source: current.source, base: serverSource}, key)
        }
        flowStore.flowYamlOrigin = source
        flowStore.revisions = undefined
        status.value = flowStore.haveChange ? "pending" : "idle"
        if (status.value === "pending") schedule()
    }

    async function keepLocal() {
        const key = bufferKey.value
        if (!conflict.value || !key) return
        const {local} = conflict.value
        conflict.value = undefined
        status.value = "syncing"
        const result = await flowStore.saveDraft({source: local})
        onSynced(key, local, result.flow?.source ?? local)
    }

    function takeServer() {
        if (!conflict.value) return
        const {server} = conflict.value
        conflict.value = undefined
        clearBuffer()
        flowStore.flow = server
        flowStore.flowYaml = server.source ?? ""
        flowStore.flowYamlOrigin = server.source ?? ""
        flowStore.revisions = undefined
        status.value = "idle"
    }

    /** Puts back the edits buffered by a previous visit, which the next sync checks against the server. */
    function restore() {
        const buffer = readBuffer()
        if (!buffer || !canAutosave.value) return
        if (buffer.source === flowStore.flowYamlOrigin) {
            clearBuffer()
            return
        }
        flowStore.flowYaml = buffer.source
        status.value = "pending"
        sync().catch(() => undefined)
    }

    /** Pushes the pending autosave now and waits for it, so a publish is the last write. */
    async function settle() {
        clearTimers()
        await inFlight?.catch(() => undefined)
        if (readBuffer() && status.value !== "conflict") await sync().catch(() => undefined)
    }

    function notePublished(revision: number) {
        clearTimeout(publishedNotice)
        justPublished.value = revision
        publishedNotice = setTimeout(() => justPublished.value = undefined, PUBLISHED_NOTICE_DURATION)
    }

    /** Pulses the Publish button, the step an action waiting on unpublished changes needs first. */
    async function pointToPublish() {
        clearTimeout(publishHighlight)
        publishHighlighted.value = false
        await nextTick()
        publishHighlighted.value = true
        publishHighlight = setTimeout(() => publishHighlighted.value = false, 2 * PUBLISHED_NOTICE_DURATION)
    }

    function onOnline() {
        if (status.value === "offline") sync().catch(() => undefined)
    }

    function start() {
        stop()
        flowStore.autosaveActive = true
        flowStore.autosaveHandler = validateBySaving
        window.addEventListener("online", onOnline)
        newFlowRestoreOffered = false
        if (flowStore.isCreating) offerNewFlowRestore()
        stopWatchers = [
            watch(() => flowStore.flowYaml, (source) => onEdit(source)),
            watch(() => flowStore.isCreating, (creating, wasCreating) => {
                if (wasCreating && !creating) clearBuffer(newFlowKey.value)
            }),
            watch(bufferKey, () => {
                clearTimers()
                conflict.value = undefined
                status.value = "idle"
                restore()
            }, {immediate: true}),
            watch(() => flowStore.flowYamlOrigin, (origin) => {
                if (readBuffer()?.source === origin) clearBuffer()
                if (!flowStore.haveChange && status.value === "pending") {
                    clearTimers()
                    status.value = "idle"
                }
            }),
            watch(unreachable, (isUnreachable) => {
                if (!isUnreachable) onOnline()
            }),
        ]
    }

    function stop() {
        stopWatchers.forEach(unwatch => unwatch())
        stopWatchers = []
        window.removeEventListener("online", onOnline)
        flowStore.autosaveActive = false
        flowStore.autosaveHandler = null
        if (status.value === "pending") sync().catch(() => undefined)
        else clearTimers()
    }

    return {
        status,
        conflict,
        justPublished,
        notePublished,
        publishHighlighted,
        pointToPublish,
        start,
        stop,
        sync,
        settle,
        keepLocal,
        takeServer,
    }
})
