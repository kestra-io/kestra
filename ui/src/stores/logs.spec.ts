import {describe, expect, it, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import {routeQueryToQueryFilters} from "../../../src/utils/queryFilters"

const searchLogs = vi.fn()
const deleteLogsByIds = vi.fn()
const deleteLogsByQuery = vi.fn()

vi.mock("@kestra-io/kestra-sdk/logs", () => ({searchLogs, deleteLogsByIds, deleteLogsByQuery}))

const {useLogsStore} = await import("../../../src/stores/logs")

describe("logs store", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        searchLogs.mockReset()
        deleteLogsByIds.mockReset()
        deleteLogsByQuery.mockReset()
    })

    it("findLogs populates logs and total from the response", async () => {
        const store = useLogsStore()

        searchLogs.mockResolvedValue({
            results: [{id: "k1", level: "INFO"}, {id: "k2", level: "WARN"}],
            total: 2,
        })

        await store.findLogs({page: 1, size: 25})

        expect(searchLogs).toHaveBeenCalledWith({page: 1, size: 25, sort: undefined, cursor: undefined, filters: []})
        expect(store.logs).toHaveLength(2)
        expect(store.logs?.[0].id).toBe("k1")
        expect(store.total).toBe(2)
    })

    it("bulkDeleteLogs sends the ids as the request body", async () => {
        const store = useLogsStore()
        deleteLogsByIds.mockResolvedValue({count: 2})

        const ids = ["key-a", "key-b"]
        await store.bulkDeleteLogs(ids)

        expect(deleteLogsByIds).toHaveBeenCalledWith({body: ids})
    })

    it("queryDeleteLogs sends filters converted from the route-query shape", async () => {
        const store = useLogsStore()
        deleteLogsByQuery.mockResolvedValue(undefined)

        const routeFilters = {"filters[level][EQUALS]": "WARN"}
        await store.queryDeleteLogs(routeFilters)

        expect(deleteLogsByQuery).toHaveBeenCalledWith({
            filters: routeQueryToQueryFilters(routeFilters),
        })
    })

    it("bulkDeleteLogs and queryDeleteLogs call distinct SDK endpoints", async () => {
        deleteLogsByIds.mockResolvedValue({})
        deleteLogsByQuery.mockResolvedValue({})

        const store = useLogsStore()
        await store.bulkDeleteLogs(["k1"])
        await store.queryDeleteLogs({})

        expect(deleteLogsByIds).toHaveBeenCalledTimes(1)
        expect(deleteLogsByQuery).toHaveBeenCalledTimes(1)
    })
})
