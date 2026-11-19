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

const {useExecutionsStore} = await import("./executions")
const {apiUrl} = await import("override/utils/route")

describe("executions store isResumeAllowed", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        get.mockReset()
    })

    test("asks the resume allowed route of the execution and answers what the server says", async () => {
        get.mockResolvedValue({data: {allowed: true}})
        const store = useExecutionsStore()

        expect(await store.isResumeAllowed("e1")).toBe(true)
        expect(get).toHaveBeenCalledWith(`${apiUrl()}/executions/e1/actions/resume/allowed`)
    })

    test("answers false to a failed check and keeps it out of the global error handling", async () => {
        const failure = {status: 500}
        get.mockRejectedValueOnce(failure)
        const store = useExecutionsStore()

        expect(await store.isResumeAllowed("e1")).toBe(false)
        expect(failure).toHaveProperty("__kestra_handled", true)
    })
})
