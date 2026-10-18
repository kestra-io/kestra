import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import {TUTORIAL_NAMESPACE} from "../utils/constants"
import type {ToastMessage} from "../utils/problem"
import {useCoreStore} from "./core"

vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    listFlowsByNamespace: vi.fn(),
}))

describe("core store", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
    })

    it("has the documented defaults for every piece of state", () => {
        const store = useCoreStore()

        expect(store.message).toBeUndefined()
        expect(store.error).toBeUndefined()
    })

    it("updates what a reader observes when setters are used", () => {
        const store = useCoreStore()
        const toastMessage: ToastMessage = {
            title: "Success",
            message: "Operation completed",
            variant: "success",
        }

        store.message = toastMessage
        store.error = 404

        expect(store.message).toEqual(toastMessage)
        expect(store.error).toBe(404)

        store.error = "Connection lost"
        expect(store.error).toBe("Connection lost")
    })

    it("shares one instance across two consumers of the store", () => {
        const consumer1 = useCoreStore()
        const consumer2 = useCoreStore()

        consumer1.message = {
            title: "Warning",
            message: "Resource deprecated",
            variant: "warning",
        }
        consumer1.error = 500

        expect(consumer2.message).toEqual({
            title: "Warning",
            message: "Resource deprecated",
            variant: "warning",
        })
        expect(consumer2.error).toBe(500)
    })

    it("gives a clean instance when resetting pinia between tests", () => {
        const store = useCoreStore()

        expect(store.message).toBeUndefined()
        expect(store.error).toBeUndefined()
    })

    it("fetches tutorial flows by namespace using the sdk", async () => {
        const store = useCoreStore()
        const mockFlows = [
            {id: "first-flow", namespace: TUTORIAL_NAMESPACE},
            {id: "second-flow", namespace: TUTORIAL_NAMESPACE},
        ]
        vi.mocked(FlowsAPI.listFlowsByNamespace).mockResolvedValue(mockFlows as never)

        const flows = await store.readTutorialFlows()

        expect(FlowsAPI.listFlowsByNamespace).toHaveBeenCalledWith({
            namespace: TUTORIAL_NAMESPACE,
        })
        expect(flows).toEqual(mockFlows)
    })
})
