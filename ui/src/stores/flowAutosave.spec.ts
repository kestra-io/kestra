import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {computed, reactive} from "vue"
import {setActivePinia} from "pinia"
import {routedPinia} from "../../tests/unit/memoryRouter"
import {markServerReachable} from "../composables/useServerReachability"

const INITIAL = "id: f\nnamespace: ns\n"
const server = {source: INITIAL}
const validation = {warnings: ["checked"]}

function javaHash(source: string) {
    let hash = 0
    for (let i = 0; i < source.length; i++) hash = (Math.imul(31, hash) + source.charCodeAt(i)) | 0
    return hash
}

const saveDraft = vi.fn(async ({source, baseSourceHash}: {source: string, baseSourceHash?: number}) => {
    if (baseSourceHash !== undefined && baseSourceHash !== javaHash(server.source)) {
        return {conflict: true, validation, flow: {id: "f", namespace: "ns", source: server.source}}
    }
    server.source = source
    return {conflict: false, validation, flow: {id: "f", namespace: "ns", source, draft: true}}
})

const flowStore = reactive({
    flow: {id: "f", namespace: "ns", source: INITIAL} as Record<string, unknown>,
    flowYaml: INITIAL,
    flowYamlOrigin: INITIAL,
    flowValidation: undefined as unknown,
    isCreating: false,
    isReadOnly: false,
    previewSource: undefined as string | undefined,
    autosaveActive: false,
    autosaveHandler: null as ((source: string) => Promise<unknown> | undefined) | null,
    revisions: undefined as unknown,
    haveChange: computed((): boolean => flowStore.flowYaml !== flowStore.flowYamlOrigin),
    saveDraft,
})

vi.mock("./flow", () => ({useFlowStore: () => flowStore}))

const messageBox = vi.fn()
vi.mock("@kestra-io/design-system", async (importOriginal) => ({
    ...await importOriginal<typeof import("@kestra-io/design-system")>(),
    KsMessageBox: (...args: unknown[]) => messageBox(...args),
}))

import {AUTOSAVE_DELAY, OFFLINE_RETRY_DELAY, sourceHash, useFlowAutosaveStore} from "./flowAutosave"

function networkError() {
    return Object.assign(new Error("Network Error"), {status: undefined})
}

describe("flowAutosave", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        localStorage.clear()
        server.source = INITIAL
        flowStore.flow = {id: "f", namespace: "ns", source: INITIAL}
        flowStore.flowYaml = INITIAL
        flowStore.flowYamlOrigin = INITIAL
        flowStore.isCreating = false
        saveDraft.mockClear()
        messageBox.mockReset()
        markServerReachable()
        setActivePinia(routedPinia())
    })

    afterEach(() => {
        useFlowAutosaveStore().stop()
        vi.useRealTimers()
    })

    it("hashes a source the way Java's String.hashCode does", () => {
        expect(sourceHash("")).toBe(0)
        expect(sourceHash("hello")).toBe(99162322)
        expect(sourceHash("id: f\nnamespace: ns\n")).toBe(javaHash("id: f\nnamespace: ns\n"))
    })

    it("answers the editor's validation with the draft save, in a single request per pause", async () => {
        const autosave = useFlowAutosaveStore()
        autosave.start()

        flowStore.flowYaml = `${INITIAL}description: a\n`
        const first = flowStore.autosaveHandler!(flowStore.flowYaml)
        await vi.advanceTimersByTimeAsync(AUTOSAVE_DELAY / 2)
        flowStore.flowYaml = `${INITIAL}description: ab\n`
        const second = flowStore.autosaveHandler!(flowStore.flowYaml)
        await vi.advanceTimersByTimeAsync(AUTOSAVE_DELAY)

        expect(saveDraft).toHaveBeenCalledExactlyOnceWith({source: `${INITIAL}description: ab\n`, baseSourceHash: sourceHash(INITIAL)})
        await expect(first).resolves.toBe(validation)
        await expect(second).resolves.toBe(validation)
        expect(flowStore.haveChange).toBe(false)
        expect(autosave.status).toBe("idle")
    })

    it("keeps the change locally while offline and pushes it once the server answers again", async () => {
        const autosave = useFlowAutosaveStore()
        autosave.start()
        saveDraft.mockRejectedValueOnce(networkError())

        flowStore.flowYaml = `${INITIAL}description: offline\n`
        await vi.advanceTimersByTimeAsync(AUTOSAVE_DELAY)

        expect(autosave.status).toBe("offline")
        expect(localStorage.length).toBe(1)

        await vi.advanceTimersByTimeAsync(OFFLINE_RETRY_DELAY)

        expect(saveDraft).toHaveBeenCalledTimes(2)
        expect(server.source).toBe(`${INITIAL}description: offline\n`)
        expect(autosave.status).toBe("idle")
        expect(localStorage.length).toBe(0)
    })

    it("reports a conflict when the server moved on, and keeps whichever side the user picks", async () => {
        const autosave = useFlowAutosaveStore()
        autosave.start()
        saveDraft.mockRejectedValueOnce(networkError())

        const local = `${INITIAL}description: mine\n`
        flowStore.flowYaml = local
        await vi.advanceTimersByTimeAsync(AUTOSAVE_DELAY)
        server.source = `${INITIAL}description: theirs\n`
        await vi.advanceTimersByTimeAsync(OFFLINE_RETRY_DELAY)

        expect(autosave.status).toBe("conflict")
        expect(server.source).toContain("theirs")

        await autosave.keepLocal()

        expect(saveDraft).toHaveBeenLastCalledWith({source: local})
        expect(server.source).toBe(local)
        expect(autosave.status).toBe("idle")
    })

    it("keeps a flow being created in this browser, and offers it back on the next visit", async () => {
        flowStore.isCreating = true
        const autosave = useFlowAutosaveStore()
        autosave.start()
        await vi.advanceTimersByTimeAsync(0)
        const unsaved = `${INITIAL}description: never created\n`

        flowStore.flowYaml = unsaved
        await vi.advanceTimersByTimeAsync(AUTOSAVE_DELAY)
        expect(saveDraft).not.toHaveBeenCalled()
        autosave.stop()

        flowStore.flowYaml = INITIAL
        messageBox.mockResolvedValueOnce("confirm")
        autosave.start()
        await vi.advanceTimersByTimeAsync(0)

        expect(messageBox).toHaveBeenCalledOnce()
        expect(flowStore.flowYaml).toBe(unsaved)
    })
})
