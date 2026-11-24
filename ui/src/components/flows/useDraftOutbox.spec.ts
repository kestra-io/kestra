import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {effectScope, nextTick, reactive, ref, type EffectScope} from "vue"
import type {QueuedDraft} from "../../utils/draftOutbox"
import type {DraftSyncOutcome} from "./useFlowEditorActions"

const syncQueuedDraft = vi.fn<() => Promise<DraftSyncOutcome>>()
const messageBox = vi.fn<() => Promise<unknown>>()
const stored = ref<QueuedDraft>()
const flowStore = reactive({
    flow: {namespace: "company.team", id: "hello", source: "server", revision: 4} as {namespace: string, id: string, source: string, revision: number} | undefined,
    flowYaml: "server",
    isCreating: false,
    queuedDraft: undefined as QueuedDraft | undefined,
    storedDraft: () => stored.value,
    discardQueuedDraft: vi.fn(() => {
        stored.value = undefined
        flowStore.queuedDraft = undefined
    }),
})

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))
vi.mock("@kestra-io/design-system", async (importOriginal) => ({
    ...await importOriginal<typeof import("@kestra-io/design-system")>(),
    KsMessageBox: messageBox,
}))
vi.mock("../../stores/flow", () => ({
    useFlowStore: () => flowStore,
}))
vi.mock("./useFlowEditorActions", () => ({
    useFlowEditorActions: () => ({syncQueuedDraft}),
}))

const queued = (source: string, baseRevision = 4): QueuedDraft => ({source, baseRevision, queuedAt: "2026-11-24T10:00:00.000Z"})

describe("useDraftOutbox", () => {
    let scope: EffectScope

    async function start(isEditing = true) {
        const {useDraftOutbox, DRAFT_SYNC_INTERVAL} = await import("./useDraftOutbox")
        scope = effectScope()
        scope.run(() => useDraftOutbox(ref(isEditing)))
        await nextTick()
        return DRAFT_SYNC_INTERVAL
    }

    beforeEach(() => {
        vi.useFakeTimers()
        vi.clearAllMocks()
        stored.value = undefined
        flowStore.flow = {namespace: "company.team", id: "hello", source: "server", revision: 4}
        flowStore.flowYaml = "server"
        flowStore.queuedDraft = undefined
    })

    afterEach(() => {
        scope.stop()
        vi.useRealTimers()
    })

    it("retries the queued draft on each interval until it is turned down", async () => {
        const interval = await start()
        syncQueuedDraft.mockResolvedValueOnce("retry").mockResolvedValueOnce("stop")
        flowStore.flowYaml = "edited"
        flowStore.queuedDraft = queued("edited")
        await nextTick()

        await vi.advanceTimersByTimeAsync(interval)
        expect(syncQueuedDraft).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(interval)
        expect(syncQueuedDraft).toHaveBeenCalledTimes(2)

        await vi.advanceTimersByTimeAsync(interval * 3)
        expect(syncQueuedDraft).toHaveBeenCalledTimes(2)
    })

    it("does not push edits made after the draft was queued", async () => {
        const interval = await start()
        flowStore.queuedDraft = queued("edited")
        flowStore.flowYaml = "edited further"
        await nextTick()

        await vi.advanceTimersByTimeAsync(interval * 2)

        expect(syncQueuedDraft).not.toHaveBeenCalled()
    })

    it("puts a draft left by a closed tab back in the editor as unsaved changes, kept in storage until it is saved", async () => {
        stored.value = queued("left behind", 3)
        messageBox.mockResolvedValue("confirm")

        await start()
        await vi.runOnlyPendingTimersAsync()

        expect(messageBox).toHaveBeenCalledOnce()
        expect(flowStore.flowYaml).toBe("left behind")
        expect(flowStore.queuedDraft).toBeUndefined()
        expect(flowStore.discardQueuedDraft).not.toHaveBeenCalled()
    })

    it("drops the stored draft when the user declines it, and never offers it outside the editor", async () => {
        stored.value = queued("left behind")
        messageBox.mockRejectedValue("cancel")

        await start(false)
        await vi.runOnlyPendingTimersAsync()
        expect(messageBox).not.toHaveBeenCalled()
        scope.stop()

        await start()
        await vi.runOnlyPendingTimersAsync()

        expect(flowStore.flowYaml).toBe("server")
        expect(flowStore.discardQueuedDraft).toHaveBeenCalledOnce()
    })
})
