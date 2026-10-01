import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {useFlowStore} from "./flow"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}}),
}))
const auth = vi.hoisted(() => ({user: undefined as {isAllowed: () => boolean} | undefined}))
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => auth,
}))
vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    validateFlows: vi.fn(() => Promise.resolve([{}])),
}))

describe("flow store", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        auth.user = undefined
    })

    it("should report a single error when its detail contains commas", () => {
        const store = useFlowStore()

        store.flowValidation = {
            errors: [{
                detail: "Unrecognized field \"expiredOnly\" (class io.kestra.plugin.core.kv.PurgeKV), not marked as ignorable",
                pointer: "/tasks/0/expiredOnly",
                path: "tasks[0].expiredOnly",
            }],
        }

        expect(store.flowErrors).toHaveLength(1)
        expect(store.flowErrors?.[0]).toContain("expiredOnly")
    })

    it("should report one error per entry, prefixed with its path when it has one", () => {
        const store = useFlowStore()

        store.flowValidation = {
            errors: [
                {detail: "must not be null", pointer: "/tasks/0/type", path: "tasks[first].type"},
                {detail: "Unable to validate the flow: boom"},
            ],
        }

        expect(store.flowErrors).toEqual([
            "tasks[first].type: must not be null",
            "Unable to validate the flow: boom",
        ])
    })

    it("should report the namespace creation denial as an error", async () => {
        const store = useFlowStore()
        store.isCreating = true
        auth.user = {isAllowed: () => false}

        await store.validateFlow({flow: "id: hello\nnamespace: company.team\n"})

        expect(store.flowErrors).toHaveLength(1)
    })
})
