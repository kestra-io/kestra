import {beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"

describe("flow draft save", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })

    it("exposes a saveAsDraft action on the flow store", async () => {
        const {useFlowStore} = await import("../../../src/stores/flow")
        const store = useFlowStore()
        expect(typeof store.saveAsDraft).toBe("function")
    })

    it("exposes save and saveAll actions on the flow store", async () => {
        const {useFlowStore} = await import("../../../src/stores/flow")
        const store = useFlowStore()
        expect(typeof store.save).toBe("function")
        expect(typeof store.saveAll).toBe("function")
    })
})
