import {beforeEach, describe, expect, test, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({
        push: vi.fn(),
        replace: vi.fn(),
        beforeEach: vi.fn(),
        afterEach: vi.fn(),
    }),
}))

const get = vi.hoisted(() => vi.fn())

vi.mock("@kestra-io/kestra-sdk", async (importOriginal) => ({
    ...await importOriginal<object>(),
    useClient: () => ({get, post: vi.fn(), put: vi.fn(), delete: vi.fn()}),
}))

// static import: the store module drags in heavy singletons (e.g. Monaco); re-importing it
// per test via vi.resetModules() re-runs those singleton registrations and throws
const {useExecutionsStore} = await import("./executions")

describe("executions store isReviewAllowed", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        get.mockReset()
    })

    test("asks the server once for every caller checking the same approval", async () => {
        get.mockResolvedValue({data: {allowed: true}})
        const store = useExecutionsStore()

        const answers = await Promise.all([1, 2, 3].map(() => store.isReviewAllowed({id: "e1", taskRunId: "tr1"})))

        expect(answers).toEqual([true, true, true])
        expect(get).toHaveBeenCalledTimes(1)
    })

    test("answers false to a failed check, keeps it out of the global error handling and asks again next time", async () => {
        const failure = {status: 500}
        get.mockRejectedValueOnce(failure).mockResolvedValueOnce({data: {allowed: true}})
        const store = useExecutionsStore()

        expect(await store.isReviewAllowed({id: "e1", taskRunId: "tr1"})).toBe(false)
        expect(failure).toHaveProperty("__kestra_handled", true)
        expect(await store.isReviewAllowed({id: "e1", taskRunId: "tr1"})).toBe(true)
    })
})
