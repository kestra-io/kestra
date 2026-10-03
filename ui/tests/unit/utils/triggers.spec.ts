import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import * as TriggersAPI from "@kestra-io/kestra-sdk/triggers"
import {searchTriggers, searchTriggersForFlow, exportTriggersAsCSV} from "../../../src/utils/triggers"

vi.mock("@kestra-io/kestra-sdk/triggers", () => ({
    searchTriggers: vi.fn(),
    searchTriggersForFlow: vi.fn(),
    exportTriggers: vi.fn(),
}))

describe("triggers utils", () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    describe("searchTriggers", () => {
        it("wraps a sort string into a one-element array", async () => {
            await searchTriggers({sort: "id:asc"})

            expect(TriggersAPI.searchTriggers).toHaveBeenCalledTimes(1)
            expect(TriggersAPI.searchTriggers).toHaveBeenCalledWith({sort: ["id:asc"]})
        })

        it("passes undefined when no sort was given, rather than [undefined] or []", async () => {
            await searchTriggers({})

            expect(TriggersAPI.searchTriggers).toHaveBeenCalledTimes(1)
            expect(TriggersAPI.searchTriggers).toHaveBeenCalledWith({sort: undefined})

            await searchTriggers({sort: undefined})
            expect(TriggersAPI.searchTriggers).toHaveBeenLastCalledWith({sort: undefined})
        })

        it("forwards every other option untouched", async () => {
            const options = {
                namespace: "io.kestra.tests",
                page: 1,
                size: 20,
                q: "search-query",
                sort: "id:desc",
            }

            await searchTriggers(options)

            expect(TriggersAPI.searchTriggers).toHaveBeenCalledTimes(1)
            expect(TriggersAPI.searchTriggers).toHaveBeenCalledWith({
                namespace: "io.kestra.tests",
                page: 1,
                size: 20,
                q: "search-query",
                sort: ["id:desc"],
            })
        })
    })

    describe("searchTriggersForFlow", () => {
        it("wraps a sort string into a one-element array and keeps namespace and flowId", async () => {
            await searchTriggersForFlow({
                namespace: "io.kestra.tests",
                flowId: "my-flow",
                sort: "id:asc",
            })

            expect(TriggersAPI.searchTriggersForFlow).toHaveBeenCalledTimes(1)
            expect(TriggersAPI.searchTriggersForFlow).toHaveBeenCalledWith({
                namespace: "io.kestra.tests",
                flowId: "my-flow",
                sort: ["id:asc"],
            })
        })

        it("passes undefined when no sort was given, rather than [undefined] or []", async () => {
            await searchTriggersForFlow({
                namespace: "io.kestra.tests",
                flowId: "my-flow",
            })

            expect(TriggersAPI.searchTriggersForFlow).toHaveBeenCalledTimes(1)
            expect(TriggersAPI.searchTriggersForFlow).toHaveBeenCalledWith({
                namespace: "io.kestra.tests",
                flowId: "my-flow",
                sort: undefined,
            })
        })

        it("forwards every other option untouched", async () => {
            await searchTriggersForFlow({
                namespace: "io.kestra.tests",
                flowId: "my-flow",
                page: 2,
                size: 50,
                sort: "executionId:desc",
            })

            expect(TriggersAPI.searchTriggersForFlow).toHaveBeenCalledTimes(1)
            expect(TriggersAPI.searchTriggersForFlow).toHaveBeenCalledWith({
                namespace: "io.kestra.tests",
                flowId: "my-flow",
                page: 2,
                size: 50,
                sort: ["executionId:desc"],
            })
        })
    })

    describe("exportTriggersAsCSV", () => {
        const mockBlobUrl = "blob:http://localhost:8080/mock-uuid"
        let createObjectURLMock: ReturnType<typeof vi.fn>
        let revokeObjectURLMock: ReturnType<typeof vi.fn>
        let clickSpy: ReturnType<typeof vi.spyOn>

        beforeEach(() => {
            createObjectURLMock = vi.fn().mockReturnValue(mockBlobUrl)
            revokeObjectURLMock = vi.fn()
            window.URL.createObjectURL = createObjectURLMock
            window.URL.revokeObjectURL = revokeObjectURLMock
            clickSpy = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})
        })

        afterEach(() => {
            clickSpy.mockRestore()
        })

        it("requests text/csv and forwards the filters", async () => {
            vi.mocked(TriggersAPI.exportTriggers).mockResolvedValueOnce("id,namespace\ntrig1,test" as any)

            const filters = {namespace: "io.kestra.tests", flowId: "flow-1"}
            await exportTriggersAsCSV({filters})

            expect(TriggersAPI.exportTriggers).toHaveBeenCalledTimes(1)
            expect(TriggersAPI.exportTriggers).toHaveBeenCalledWith(
                {filters},
                {headers: {Accept: "text/csv"}},
            )
        })

        it("creates an object URL, triggers the download, and revokes the URL afterwards", async () => {
            const csvContent = "id,namespace\ntrig1,test"
            vi.mocked(TriggersAPI.exportTriggers).mockResolvedValueOnce(csvContent as any)

            const appendChildSpy = vi.spyOn(document.body, "appendChild")

            await exportTriggersAsCSV({filters: {}})

            expect(createObjectURLMock).toHaveBeenCalledTimes(1)
            const blobArg = createObjectURLMock.mock.calls[0][0] as Blob
            expect(blobArg).toBeInstanceOf(Blob)
            expect(blobArg.type).toBe("text/csv")

            expect(appendChildSpy).toHaveBeenCalledTimes(1)
            const appendedElement = appendChildSpy.mock.calls[0][0] as HTMLAnchorElement
            expect(appendedElement.tagName).toBe("A")
            expect(appendedElement.href).toBe(mockBlobUrl)
            expect(appendedElement.getAttribute("download")).toBe("triggers.csv")

            expect(clickSpy).toHaveBeenCalledTimes(1)
            expect(revokeObjectURLMock).toHaveBeenCalledTimes(1)
            expect(revokeObjectURLMock).toHaveBeenCalledWith(mockBlobUrl)

            // Verify the link is removed from DOM
            expect(document.body.contains(appendedElement)).toBe(false)
        })
    })
})
