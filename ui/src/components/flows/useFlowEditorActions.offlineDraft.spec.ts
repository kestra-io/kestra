import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const saveAll = vi.fn()
const queueDraft = vi.fn()
const flowState = {flow: {draft: true}, isCreating: false, filesSaveAll: null, saveAll, queueDraft}
const warning = vi.fn()
const requestError = vi.fn()

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn()}),
}))
vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {}}),
}))
vi.mock("../../utils/toast", async (importOriginal) => ({
    ...await importOriginal<typeof import("../../utils/toast")>(),
    useToast: () => ({warning, requestError}),
}))
vi.mock("../../stores/flow", async (importOriginal) => ({
    ...await importOriginal<typeof import("../../stores/flow")>(),
    useFlowStore: () => flowState,
}))

const unreachable = () => Object.assign(new TypeError("Failed to fetch"), {__kestra_unreachable: true})

describe("useFlowEditorActions - saving while the server is unreachable", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
        flowState.flow = {draft: true}
        queueDraft.mockReturnValue(true)
    })

    it("keeps a draft on the device instead of reporting a failed save", async () => {
        saveAll.mockRejectedValue(unreachable())
        const {useFlowEditorActions} = await import("./useFlowEditorActions")

        await useFlowEditorActions().saveAsDraft()

        expect(queueDraft).toHaveBeenCalledOnce()
        expect(warning).toHaveBeenCalledExactlyOnceWith("offlineDraft.queued", "offlineDraft.queued title")
        expect(requestError).not.toHaveBeenCalled()
    })

    it("queues a draft save and never a publish", async () => {
        saveAll.mockRejectedValue(unreachable())
        const {useFlowEditorActions} = await import("./useFlowEditorActions")
        const actions = useFlowEditorActions()

        await actions.publish()
        expect(queueDraft).not.toHaveBeenCalled()
        expect(requestError).toHaveBeenCalledOnce()

        await actions.saveAsDraft()
        expect(queueDraft).toHaveBeenCalledOnce()
        expect(requestError).toHaveBeenCalledOnce()
    })

    it("reports the failure when the server did answer, or when the browser refused to store the draft", async () => {
        const {useFlowEditorActions} = await import("./useFlowEditorActions")
        const actions = useFlowEditorActions()

        saveAll.mockRejectedValue(Object.assign(new Error("invalid"), {status: 422}))
        await actions.saveAsDraft()
        expect(queueDraft).not.toHaveBeenCalled()

        saveAll.mockRejectedValue(unreachable())
        queueDraft.mockReturnValue(false)
        await actions.saveAsDraft()

        expect(warning).not.toHaveBeenCalled()
        expect(requestError).toHaveBeenCalledTimes(2)
    })

    it("tells the retry loop to wait for the server, and to stop once the user or the server turned the save down", async () => {
        const {useFlowEditorActions} = await import("./useFlowEditorActions")
        const actions = useFlowEditorActions()

        saveAll.mockRejectedValueOnce(unreachable())
        expect(await actions.syncQueuedDraft()).toBe("retry")

        saveAll.mockResolvedValueOnce("no_op")
        expect(await actions.syncQueuedDraft()).toBe("stop")

        saveAll.mockResolvedValueOnce("saved")
        expect(await actions.syncQueuedDraft()).toBe("synced")
        expect(saveAll).toHaveBeenLastCalledWith(true)
    })
})
