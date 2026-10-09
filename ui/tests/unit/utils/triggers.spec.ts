import {describe, it, expect, vi, onTestFinished} from "vitest"
import * as TriggersAPI from "@kestra-io/kestra-sdk/triggers"
import {searchTriggers, searchTriggersForFlow, exportTriggersAsCSV} from "../../../src/utils/triggers"

vi.mock("@kestra-io/kestra-sdk/triggers", () => ({
    searchTriggers: vi.fn(),
    searchTriggersForFlow: vi.fn(),
    exportTriggers: vi.fn(),
}))

describe("searchTriggers", () => {
    it("wraps the sort in an array and forwards the other options", async () => {
        await searchTriggers({namespace: "io.kestra.tests", page: 2, sort: "id:desc"})

        expect(TriggersAPI.searchTriggers).toHaveBeenLastCalledWith({namespace: "io.kestra.tests", page: 2, sort: ["id:desc"]})
    })

    it("sends no sort when none is given", async () => {
        await searchTriggers({page: 1})

        expect(TriggersAPI.searchTriggers).toHaveBeenLastCalledWith({page: 1, sort: undefined})
    })
})

describe("searchTriggersForFlow", () => {
    it("wraps the sort in an array and keeps namespace and flowId", async () => {
        await searchTriggersForFlow({namespace: "io.kestra.tests", flowId: "my-flow", sort: "id:asc"})

        expect(TriggersAPI.searchTriggersForFlow).toHaveBeenLastCalledWith({namespace: "io.kestra.tests", flowId: "my-flow", sort: ["id:asc"]})
    })
})

describe("exportTriggersAsCSV", () => {
    it("requests CSV for the given filters and downloads it as triggers.csv", async () => {
        vi.mocked(TriggersAPI.exportTriggers).mockResolvedValue("id,namespace\ntrig1,test")
        const original = {createObjectURL: URL.createObjectURL, revokeObjectURL: URL.revokeObjectURL}
        const revokeObjectURL = vi.fn()
        Object.assign(URL, {createObjectURL: () => "blob:triggers", revokeObjectURL})
        const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})
        onTestFinished(() => {
            Object.assign(URL, original)
            click.mockRestore()
        })

        await exportTriggersAsCSV({filters: {namespace: "io.kestra.tests"}})

        expect(TriggersAPI.exportTriggers).toHaveBeenLastCalledWith({filters: {namespace: "io.kestra.tests"}}, {headers: {Accept: "text/csv"}})
        const link = click.mock.contexts[0] as HTMLAnchorElement
        expect(link.getAttribute("download")).toBe("triggers.csv")
        expect(link.getAttribute("href")).toBe("blob:triggers")
        expect(revokeObjectURL).toHaveBeenCalledWith("blob:triggers")
    })
})
