import {beforeEach, describe, expect, it, vi} from "vitest"
import {setActivePinia, createPinia} from "pinia"

import type {Level, LogEntry} from "@kestra-io/kestra-sdk"
import type {searchLogs as searchLogsSdk} from "@kestra-io/kestra-sdk/logs"

type SearchLogsResponse = Awaited<ReturnType<typeof searchLogsSdk>>

const searchLogs = vi.fn<typeof searchLogsSdk>()

vi.mock("@kestra-io/kestra-sdk/logs", () => ({
    searchLogs: (...args: Parameters<typeof searchLogsSdk>) => searchLogs(...args),
    deleteLogsFromFlow: vi.fn(),
}))

import {useLogsStore} from "./logs"

const log = (level: Level): LogEntry => ({namespace: "test", flowId: "logs", level, message: `a ${level} line`})

const levelFilterOf = (params: Parameters<typeof searchLogsSdk>[0]) => params?.filters
    ?.find(filter => filter.field === "level")?.value

describe("logs store search ordering", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        searchLogs.mockReset()
    })

    it("ignores a superseded search that answers after the newest one", async () => {
        const store = useLogsStore()

        let resolveStale: (value: SearchLogsResponse) => void = () => {}
        searchLogs.mockImplementation(params => levelFilterOf(params) === "DEBUG"
            ? new Promise<SearchLogsResponse>((resolve) => {
                resolveStale = resolve
            })
            : Promise.resolve({results: [log("WARN")], total: 1, type: "OFFSET"}))

        const stale = store.findLogs({page: 1, size: 25, "filters[level][GREATER_THAN_OR_EQUAL_TO]": "DEBUG"})
        const newest = store.findLogs({page: 1, size: 25, "filters[level][GREATER_THAN_OR_EQUAL_TO]": "WARN"})

        resolveStale({results: [log("DEBUG"), log("INFO"), log("WARN")], total: 3, type: "OFFSET"})
        await Promise.all([stale, newest])

        expect(store.logs).toEqual([log("WARN")])
        expect(store.total).toBe(1)
    })

    it("publishes the newest search even when it answers last", async () => {
        const store = useLogsStore()

        searchLogs.mockImplementation(params => Promise.resolve(
            params?.page === 1
                ? {results: [log("DEBUG")], total: 1, type: "OFFSET"}
                : {results: [log("ERROR")], total: 1, type: "OFFSET"},
        ))

        await store.findLogs({page: 1, size: 25})
        await store.findLogs({page: 2, size: 25})

        expect(store.logs).toEqual([log("ERROR")])
    })
})
