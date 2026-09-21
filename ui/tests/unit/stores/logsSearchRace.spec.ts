import {beforeEach, describe, expect, it, vi} from "vitest"
import {setActivePinia, createPinia} from "pinia"

const searchLogs = vi.fn()

vi.mock("@kestra-io/kestra-sdk/logs", () => ({
    searchLogs: (...args: any[]) => searchLogs(...args),
    deleteLogsFromFlow: vi.fn(),
}))

import {useLogsStore} from "../../../src/stores/logs"

const log = (level: string) => ({level, message: `a ${level} line`})

const levelFilterOf = (params: any) => params.filters
    ?.find((filter: any) => filter.field === "level")?.value

describe("logs store search ordering", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        searchLogs.mockReset()
    })

    it("ignores a superseded search that answers after the newest one", async () => {
        const store = useLogsStore()

        let resolveStale: (value: unknown) => void = () => {}
        searchLogs.mockImplementation((params: any) => levelFilterOf(params) === "DEBUG"
            ? new Promise((resolve) => {
                resolveStale = resolve
            })
            : Promise.resolve({results: [log("WARN")], total: 1}))

        const stale = store.findLogs({page: 1, size: 25, "filters[level][GREATER_THAN_OR_EQUAL_TO]": "DEBUG"})
        const newest = store.findLogs({page: 1, size: 25, "filters[level][GREATER_THAN_OR_EQUAL_TO]": "WARN"})

        resolveStale({results: [log("DEBUG"), log("INFO"), log("WARN")], total: 3})
        await Promise.all([stale, newest])

        expect(store.logs).toEqual([log("WARN")])
        expect(store.total).toBe(1)
    })

    it("publishes the newest search even when it answers last", async () => {
        const store = useLogsStore()

        searchLogs.mockImplementation((params: any) => Promise.resolve(
            params.page === 1
                ? {results: [log("DEBUG")], total: 1}
                : {results: [log("ERROR")], total: 1},
        ))

        await store.findLogs({page: 1, size: 25})
        await store.findLogs({page: 2, size: 25})

        expect(store.logs).toEqual([log("ERROR")])
    })
})
