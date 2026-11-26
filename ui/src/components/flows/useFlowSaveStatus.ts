import {computed} from "vue"
import {useFlowStore} from "../../stores/flow"
import {useFlowAutosaveStore} from "../../stores/flowAutosave"

export type FlowSaveState = "upToDate" | "saving" | "draft" | "invalid" | "offline" | "published"

export function useFlowSaveStatus() {
    const flowStore = useFlowStore()
    const autosaveStore = useFlowAutosaveStore()

    // Drafts collapse into one revision on top of the published one, so it sits right below a draft head.
    const publishedRevision = computed((): number | undefined => {
        const flow = flowStore.flow
        if (!flow?.revision) return undefined
        if (!flow.draft) return flow.revision
        return flowStore.hasPublishedRevision ? flow.revision - 1 : undefined
    })

    const errorCount = computed(() => flowStore.flowErrors?.length ?? 0)

    const state = computed((): FlowSaveState | undefined => {
        if (flowStore.isCreating || !flowStore.flow) return undefined
        switch (autosaveStore.status) {
        case "offline":
        case "conflict": return "offline"
        case "pending":
        case "syncing": return "saving"
        }
        if (autosaveStore.justPublished !== undefined) return "published"
        if (flowStore.flow.draft) return errorCount.value > 0 ? "invalid" : "draft"
        return "upToDate"
    })

    const canPublish = computed(() =>
        !flowStore.isReadOnly && (state.value === "draft" || (state.value === "upToDate" && flowStore.hasDirtyEditorFiles)),
    )

    /** The revision Execute names while the editor holds changes it would not run. */
    const executeRevision = computed(() =>
        state.value === undefined || state.value === "upToDate" || state.value === "published" ? undefined : publishedRevision.value,
    )

    return {state, publishedRevision, errorCount, canPublish, executeRevision}
}
