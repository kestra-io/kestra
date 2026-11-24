import {h, watch, type Ref} from "vue"
import {useI18n} from "vue-i18n"
import {useEventListener, useIntervalFn} from "@vueuse/core"
import {dateUtils, KsMessageBox} from "@kestra-io/design-system"

import {useFlowStore} from "../../stores/flow"
import {useServerReachability} from "../../composables/useServerReachability"
import {useFlowEditorActions} from "./useFlowEditorActions"

export const DRAFT_SYNC_INTERVAL = 15_000

/**
 * Owns the retry of a draft queued while the server was unreachable, and offers back one a closed tab left behind.
 * Mount it once per flow page: every instance runs its own retry timer.
 */
export function useDraftOutbox(isEditing: Ref<boolean>) {
    const flowStore = useFlowStore()
    const {unreachable} = useServerReachability()
    const {syncQueuedDraft} = useFlowEditorActions()
    const {t} = useI18n()

    let syncing = false

    async function sync() {
        const queued = flowStore.queuedDraft
        // Edits made since the draft was queued were never asked to be saved, so they are not pushed on the user's behalf.
        if (syncing || !queued || queued.source !== flowStore.flowYaml) return
        syncing = true
        try {
            if (await syncQueuedDraft() === "stop") pause()
        } finally {
            syncing = false
        }
    }

    const {pause, resume} = useIntervalFn(sync, DRAFT_SYNC_INTERVAL, {immediate: false})

    watch(() => flowStore.queuedDraft, (queued) => {
        if (queued) resume()
        else pause()
    }, {immediate: true})

    watch(unreachable, (isUnreachable) => {
        if (!isUnreachable) void sync()
    })

    useEventListener(window, "online", () => void sync())

    async function offerRestore() {
        const flow = flowStore.flow
        if (!isEditing.value || flowStore.isCreating || !flow || flowStore.queuedDraft) return
        const stored = flowStore.storedDraft()
        if (!stored) return
        if (stored.source === flow.source) {
            flowStore.discardQueuedDraft()
            return
        }

        const changedOnServer = stored.baseRevision !== flow.revision
        const restore = await KsMessageBox({
            title: t("offlineDraft.restore.title"),
            message: () => h("div", null, [
                h("p", null, t("offlineDraft.restore.message", {date: dateUtils.dateFilter(stored.queuedAt)})),
                changedOnServer ? h("p", null, t("offlineDraft.restore.changed")) : null,
            ]),
            type: "warning",
            showCancelButton: true,
            confirmButtonText: t("restore"),
            cancelButtonText: t("offlineDraft.restore.discard"),
            showClose: false,
            closeOnClickModal: false,
            closeOnPressEscape: false,
        }).then(() => true).catch(() => false)

        if (restore) {
            // Left in storage, and out of `queuedDraft`: the user reviews and saves it, and that save clears it.
            flowStore.flowYaml = stored.source
        } else {
            flowStore.discardQueuedDraft()
        }
    }

    watch(
        () => [isEditing.value, flowStore.flow?.namespace, flowStore.flow?.id] as const,
        () => void offerRestore(),
        {immediate: true},
    )
}
