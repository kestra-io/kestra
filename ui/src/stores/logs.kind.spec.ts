import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

import type {searchLogs as searchLogsSdk} from "@kestra-io/kestra-sdk/logs"

const searchLogs = vi.fn<typeof searchLogsSdk>()

vi.mock("@kestra-io/kestra-sdk/logs", () => ({
    searchLogs: (...args: Parameters<typeof searchLogsSdk>) => searchLogs(...args),
}))

describe("logs store kind filter", () => {
    beforeEach(() => {
        searchLogs.mockReset()
        searchLogs.mockResolvedValue({results: [], total: 0, type: "OFFSET"})
        setActivePinia(createPinia())
    })

    it("should also match a null kind when NORMAL is selected, so normal runs stored without a kind match", async () => {
        const {useLogsStore} = await import("./logs")

        await useLogsStore().findLogs({page: 1, size: 25, "filters[kind][IN]": "NORMAL", "filters[namespace][EQUALS]": "company.team"})

        expect(searchLogs.mock.calls[0]?.[0]?.filters).toEqual([
            {logical: "or", children: [{field: "kind", operation: "IS_NULL"}, {field: "kind", operation: "IN", value: "NORMAL"}]},
            {field: "namespace", operation: "EQUALS", value: "company.team"},
        ])
    })
})
