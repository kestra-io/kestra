import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const {mockSearchFlowsBySourceCode, mockSearchNamespaceFiles, mockAutocompleteNamespaces, mockListAllKeys, mockListSecrets} = vi.hoisted(() => ({
    mockSearchFlowsBySourceCode: vi.fn(),
    mockSearchNamespaceFiles: vi.fn(),
    mockAutocompleteNamespaces: vi.fn(),
    mockListAllKeys: vi.fn(),
    mockListSecrets: vi.fn(),
}))

vi.mock("@kestra-io/kestra-sdk/flows", () => ({searchFlowsBySourceCode: mockSearchFlowsBySourceCode}))
vi.mock("@kestra-io/kestra-sdk/files", () => ({searchNamespaceFiles: mockSearchNamespaceFiles}))
vi.mock("@kestra-io/kestra-sdk/namespaces", () => ({autocompleteNamespaces: mockAutocompleteNamespaces}))
vi.mock("@kestra-io/kestra-sdk/kv", () => ({listAllKeys: mockListAllKeys}))
vi.mock("@kestra-io/kestra-sdk/secrets", () => ({listSecrets: mockListSecrets}))

import {useCrossResourceSearchStore} from "../../../src/stores/crossResourceSearch"

const flowFilters = {caseSensitive: false, wholeWord: false, regex: false, scope: "ALL" as const}

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((r) => {
        resolve = r
    })
    return {promise, resolve}
}

describe("useCrossResourceSearchStore", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        mockSearchFlowsBySourceCode.mockReset()
        mockSearchNamespaceFiles.mockReset()
        mockAutocompleteNamespaces.mockReset()
        mockListAllKeys.mockReset()
        mockListSecrets.mockReset()
    })

    it("only searches the selected types", async () => {
        mockSearchFlowsBySourceCode.mockResolvedValue({results: []})

        const store = useCrossResourceSearchStore()
        await store.search({types: ["flows"], query: "us-east-1", ...flowFilters})

        expect(mockSearchFlowsBySourceCode).toHaveBeenCalledOnce()
        expect(mockSearchNamespaceFiles).not.toHaveBeenCalled()
        expect(mockListAllKeys).not.toHaveBeenCalled()
        expect(mockListSecrets).not.toHaveBeenCalled()
        expect(store.files.status).toBe("idle")
        expect(store.kv.status).toBe("idle")
        expect(store.secrets.status).toBe("idle")
    })

    it("fans namespace files out over one request per listable namespace, isolating failures", async () => {
        mockAutocompleteNamespaces.mockResolvedValue(["company.a", "company.b", "company.c"])
        mockSearchNamespaceFiles.mockImplementation(({namespace}: {namespace: string}) => {
            if (namespace === "company.b") return Promise.reject(new Error("timeout"))
            return Promise.resolve([`scripts/${namespace}.py`])
        })

        const store = useCrossResourceSearchStore()
        await store.search({types: ["files"], query: "us-east-1", ...flowFilters})

        expect(mockSearchNamespaceFiles).toHaveBeenCalledTimes(3)
        expect(store.files.status).toBe("done")
        expect(store.filesNamespacesFailed.map((n) => n.namespace)).toEqual(["company.b"])
        expect(store.filesMatchCount).toBe(2)
        expect(store.files.namespaces.find((n) => n.namespace === "company.a")?.paths).toEqual(["scripts/company.a.py"])
    })

    it("bounds the namespace-files fan-out to a single namespace when one is selected", async () => {
        mockSearchNamespaceFiles.mockResolvedValue(["scripts/a.py"])

        const store = useCrossResourceSearchStore()
        await store.search({types: ["files"], query: "us-east-1", namespace: "company.a", ...flowFilters})

        expect(mockAutocompleteNamespaces).not.toHaveBeenCalled()
        expect(mockSearchNamespaceFiles).toHaveBeenCalledOnce()
        expect(mockSearchNamespaceFiles).toHaveBeenCalledWith(expect.objectContaining({namespace: "company.a", q: "us-east-1"}))
    })

    it("retries a single failed namespace without refetching the others", async () => {
        mockAutocompleteNamespaces.mockResolvedValue(["company.a", "company.b"])
        mockSearchNamespaceFiles
            .mockResolvedValueOnce(["scripts/a.py"])
            .mockRejectedValueOnce(new Error("timeout"))
            .mockResolvedValueOnce(["scripts/b.py"])

        const store = useCrossResourceSearchStore()
        await store.search({types: ["files"], query: "us-east-1", ...flowFilters})
        expect(store.filesNamespacesFailed.map((n) => n.namespace)).toEqual(["company.b"])

        await store.retryNamespaceFiles("company.b")

        expect(mockSearchNamespaceFiles).toHaveBeenCalledTimes(3)
        expect(store.filesNamespacesFailed).toEqual([])
        expect(store.files.namespaces.find((n) => n.namespace === "company.b")?.paths).toEqual(["scripts/b.py"])
    })

    it("groups KV results by namespace using a QUERY + NAMESPACE filter", async () => {
        mockListAllKeys.mockResolvedValue({
            results: [
                {namespace: "company.a", key: "landing-bucket-us-east-1", updateDate: "2026-01-01T00:00:00Z"},
                {namespace: "company.b", key: "warehouse-us-east-1"},
            ],
        })

        const store = useCrossResourceSearchStore()
        await store.search({types: ["kv"], query: "us-east-1", namespace: "company.a", ...flowFilters})

        expect(mockListAllKeys).toHaveBeenCalledWith(expect.objectContaining({
            filters: [
                {field: "q", operation: "EQUALS", value: "us-east-1"},
                {field: "namespace", operation: "EQUALS", value: "company.a"},
            ],
        }))
        expect(store.kv.groups).toEqual([
            {namespace: "company.a", matches: [{key: "landing-bucket-us-east-1", updateDate: "2026-01-01T00:00:00Z"}]},
            {namespace: "company.b", matches: [{key: "warehouse-us-east-1", updateDate: undefined}]},
        ])
    })

    it("marks a type as failed on request error without affecting the others", async () => {
        mockSearchFlowsBySourceCode.mockRejectedValue({response: {data: {message: "Invalid regular expression"}}})
        mockListSecrets.mockResolvedValue({results: [{namespace: "company.a", key: "aws-us-east-1-access-key"}]})

        const store = useCrossResourceSearchStore()
        await store.search({types: ["flows", "secrets"], query: "region:\\s*us-east-[1", regex: true, caseSensitive: false, wholeWord: false, scope: "ALL"})

        expect(store.flows.status).toBe("failed")
        expect(store.flows.errorMessage).toBe("Invalid regular expression")
        expect(store.secrets.status).toBe("done")
        expect(store.secretsMatchCount).toBe(1)
    })

    it("builds an ordered flat selection list spanning every searched type", async () => {
        mockSearchFlowsBySourceCode.mockResolvedValue({
            results: [{namespace: "ns", id: "flow", editable: true, matches: [{line: 4, column: 1, snippet: "x"}]}],
        })
        mockAutocompleteNamespaces.mockResolvedValue(["ns"])
        mockSearchNamespaceFiles.mockResolvedValue(["scripts/a.py"])
        mockListAllKeys.mockResolvedValue({results: [{namespace: "ns", key: "kv-key"}]})
        mockListSecrets.mockResolvedValue({results: [{namespace: "ns", key: "secret-key"}]})

        const store = useCrossResourceSearchStore()
        await store.search({types: ["flows", "files", "kv", "secrets"], query: "x", ...flowFilters})

        expect(store.flatSelections).toEqual([
            {type: "flows", namespace: "ns", id: "flow", line: 4, column: 1},
            {type: "files", namespace: "ns", path: "scripts/a.py"},
            {type: "kv", namespace: "ns", key: "kv-key"},
            {type: "secrets", namespace: "ns", key: "secret-key"},
        ])
    })

    it("keeps the newest results when an older search resolves last", async () => {
        const slowFirst = deferred<{results: unknown[]}>()
        const fastSecond = deferred<{results: unknown[]}>()
        mockSearchFlowsBySourceCode
            .mockReturnValueOnce(slowFirst.promise)
            .mockReturnValueOnce(fastSecond.promise)

        const store = useCrossResourceSearchStore()
        const first = store.search({types: ["flows"], query: "us-east", ...flowFilters})
        const second = store.search({types: ["flows"], query: "us-east-1", ...flowFilters})

        fastSecond.resolve({results: [{namespace: "ns", id: "newer", editable: true, matches: [{line: 1, column: 0, snippet: "us-east-1"}]}]})
        await second

        slowFirst.resolve({results: [{namespace: "ns", id: "older", editable: true, matches: [{line: 9, column: 0, snippet: "us-east"}]}]})
        await first

        expect(store.flows.results).toHaveLength(1)
        expect(store.flows.results[0].id).toBe("newer")
    })

    it("discards a namespace-file retry once the query has moved on", async () => {
        mockAutocompleteNamespaces.mockResolvedValue(["ns"])
        mockSearchNamespaceFiles.mockRejectedValueOnce(new Error("timed out"))

        const store = useCrossResourceSearchStore()
        await store.search({types: ["files"], query: "us-east-1", ...flowFilters})
        expect(store.files.namespaces[0].status).toBe("failed")

        const slowRetry = deferred<string[]>()
        mockSearchNamespaceFiles.mockReturnValueOnce(slowRetry.promise)
        const staleRetry = store.retryNamespaceFiles("ns")

        mockSearchNamespaceFiles.mockResolvedValueOnce(["scripts/fresh.py"])
        await store.search({types: ["files"], query: "eu-west-1", ...flowFilters})

        slowRetry.resolve(["scripts/stale.py"])
        await staleRetry

        expect(store.files.namespaces[0].paths).toEqual(["scripts/fresh.py"])
    })
    it("retries under the query that produced the failure, not whatever is in the box now", async () => {
        mockAutocompleteNamespaces.mockResolvedValue(["ns"])
        mockSearchNamespaceFiles.mockRejectedValueOnce(new Error("timed out"))

        const store = useCrossResourceSearchStore()
        await store.search({types: ["files"], query: "us-east-1", ...flowFilters})
        expect(store.files.namespaces[0].status).toBe("failed")

        // The user edits the box; the 300ms debounce has not fired a new search yet.
        mockSearchNamespaceFiles.mockResolvedValueOnce(["scripts/whatever.py"])
        await store.retryNamespaceFiles("ns")

        expect(mockSearchNamespaceFiles).toHaveBeenLastCalledWith({namespace: "ns", q: "us-east-1"})
    })
})
