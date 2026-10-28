import {beforeEach, describe, expect, it, vi} from "vitest"
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

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        patch: vi.fn(),
        delete: vi.fn(),
    }),
}))

const {listLogsFromExecutionMock} = vi.hoisted(() => ({
    listLogsFromExecutionMock: vi.fn(),
}))
vi.mock("@kestra-io/kestra-sdk/logs", () => ({
    listLogsFromExecution: listLogsFromExecutionMock,
}))

// static import: the store module drags in heavy singletons (e.g. Monaco); re-importing it
// per test via vi.resetModules() re-runs those singleton registrations and throws
const {useExecutionsStore} = await import("./executions")

describe("executions store loadLogs", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        listLogsFromExecutionMock.mockReset()
        listLogsFromExecutionMock.mockResolvedValue([])
    })

    it("does not send pagination or sort parameters by default", async () => {
        // When
        await useExecutionsStore().loadLogs({executionId: "e1", store: false})

        // Then
        expect(listLogsFromExecutionMock).toHaveBeenCalledWith({executionId: "e1", filters: []})
    })

    it("forwards page, size and sort to the SDK", async () => {
        // When
        await useExecutionsStore().loadLogs({
            executionId: "e1",
            store: false,
            page: 1,
            size: 4,
            sort: ["timestamp:desc"],
        })

        // Then
        expect(listLogsFromExecutionMock).toHaveBeenCalledWith({
            executionId: "e1",
            filters: [],
            page: 1,
            size: 4,
            sort: ["timestamp:desc"],
        })
    })

    it("keeps the loaded logs out of the store when store is false", async () => {
        // Given
        const logs = [{message: "boom"}]
        listLogsFromExecutionMock.mockResolvedValue(logs)
        const store = useExecutionsStore()

        // When
        const result = await store.loadLogs({executionId: "e1", store: false})

        // Then
        expect(result).toEqual(logs)
        expect(store.logs).toEqual([])
    })
})
