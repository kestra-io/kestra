import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {useFlowStore} from "./flow"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}}),
}))
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: undefined}),
}))

describe("flow store", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it("should report a single error when a constraint message contains commas", () => {
        const store = useFlowStore()

        store.flowValidation = {
            constraints: "Validation error: Unrecognized field \"expiredOnly\" (class io.kestra.plugin.core.kv.PurgeKV), not marked as ignorable (5 known properties: \"keyPattern\", \"namespaces\", \"namespacePattern\", \"behavior\", \"includeChildNamespaces\"])",
        }

        expect(store.flowErrors).toHaveLength(1)
        expect(store.flowErrors?.[0]).toContain("expiredOnly")
    })

    it("should report one error per violation when the backend joins them with newlines", () => {
        const store = useFlowStore()

        store.flowValidation = {
            constraints: "Validation error: tasks[first].type: must not be null\ntasks[second].id: must not be empty\n",
        }

        expect(store.flowErrors).toEqual([
            "Validation error: tasks[first].type: must not be null",
            "tasks[second].id: must not be empty",
        ])
    })
})
