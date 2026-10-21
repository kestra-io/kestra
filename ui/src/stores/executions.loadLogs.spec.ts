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

const listLogsFromExecution = vi.fn()

vi.mock("@kestra-io/kestra-sdk/logs", () => ({listLogsFromExecution}))

const {useExecutionsStore} = await import("./executions")

describe("executions store loadLogs", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        listLogsFromExecution.mockReset()
    })

    test("passes page and size to listLogsFromExecution when paginated", async () => {
        listLogsFromExecution.mockResolvedValue([])
        const store = useExecutionsStore()

        await store.loadLogs({
            executionId: "exec-1",
            params: {taskRunId: "tr-1", minLevel: "ERROR"},
            size: 100,
        })

        expect(listLogsFromExecution).toHaveBeenCalledWith({
            executionId: "exec-1",
            page: undefined,
            size: 100,
            filters: expect.any(Array),
        })
    })

    test("extracts page and size from params if provided there", async () => {
        listLogsFromExecution.mockResolvedValue([])
        const store = useExecutionsStore()

        await store.loadLogs({
            executionId: "exec-1",
            params: {taskRunId: "tr-1", size: 100, page: 2},
        })

        expect(listLogsFromExecution).toHaveBeenCalledWith({
            executionId: "exec-1",
            page: 2,
            size: 100,
            filters: expect.any(Array),
        })
    })
})
