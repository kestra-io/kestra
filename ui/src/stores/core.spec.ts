import {describe, it, expect, beforeEach, vi} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import {useCoreStore} from "./core"
import {TUTORIAL_NAMESPACE} from "../utils/constants"

vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    listFlowsByNamespace: vi.fn(),
}))

describe("useCoreStore", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
    })

    it("initializes with documented defaults", () => {
        const store = useCoreStore()

        expect(store.message).toBeUndefined()
        expect(store.error).toBeUndefined()
    })

    it("updates message and error state correctly", () => {
        const store = useCoreStore()

        store.message = {title: "Success", content: "Flow created"}
        store.error = 404

        expect(store.message).toEqual({title: "Success", content: "Flow created"})
        expect(store.error).toBe(404)

        store.error = "Internal Server Error"
        expect(store.error).toBe("Internal Server Error")
    })

    it("provides a clean instance when Pinia is reset between tests", () => {
        const store = useCoreStore()

        store.message = {title: "Stale", content: "Should be isolated"}
        store.error = "Error"

        setActivePinia(createPinia())
        const freshStore = useCoreStore()

        expect(freshStore.message).toBeUndefined()
        expect(freshStore.error).toBeUndefined()
    })

    it("calls FlowsAPI.listFlowsByNamespace with TUTORIAL_NAMESPACE and returns flows", async () => {
        const mockFlows = [
            {id: "tutorial-1", namespace: TUTORIAL_NAMESPACE, disabled: false},
            {id: "tutorial-2", namespace: TUTORIAL_NAMESPACE, disabled: false},
        ]
        vi.mocked(FlowsAPI.listFlowsByNamespace).mockResolvedValue(mockFlows as never)

        const store = useCoreStore()
        const flows = await store.readTutorialFlows()

        expect(FlowsAPI.listFlowsByNamespace).toHaveBeenCalledTimes(1)
        expect(FlowsAPI.listFlowsByNamespace).toHaveBeenCalledWith({namespace: TUTORIAL_NAMESPACE})
        expect(flows).toEqual(mockFlows)
    })
})
