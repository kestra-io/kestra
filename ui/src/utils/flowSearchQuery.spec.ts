import {describe, expect, it} from "vitest"
import {parseFlowsSearchQuery} from "./flowSearchQuery"

describe("parseFlowsSearchQuery", () => {
    it("returns empty searchQuery and undefined namespace when query is empty", () => {
        const result = parseFlowsSearchQuery({})
        expect(result.searchQuery).toBe("")
        expect(result.filteredNamespace).toBeUndefined()
        expect(result.sourceSearchQuery).toEqual({q: ""})
    })

    it("parses direct q parameter and namespace prefix", () => {
        const result = parseFlowsSearchQuery({
            q: "my-query",
            "filters[namespace][PREFIX]": "company.team",
        })
        expect(result.searchQuery).toBe("my-query")
        expect(result.filteredNamespace).toBe("company.team")
        expect(result.sourceSearchQuery).toEqual({
            q: "my-query",
            namespace: "company.team",
        })
    })

    it("parses filter-encoded q parameter", () => {
        const result = parseFlowsSearchQuery({
            "filters[q][CONTAINS]": "my-query",
            "filters[namespace][EQUALS]": "company.team",
        })
        expect(result.searchQuery).toBe("my-query")
        expect(result.filteredNamespace).toBe("company.team")
        expect(result.sourceSearchQuery).toEqual({
            q: "my-query",
            namespace: "company.team",
        })
    })

    it("does not forward namespace when comparator is not affirmative (e.g. NOT_IN, CONTAINS)", () => {
        const result = parseFlowsSearchQuery({
            "filters[q][CONTAINS]": "my-query",
            "filters[namespace][NOT_IN]": "company.team",
        })
        expect(result.searchQuery).toBe("my-query")
        expect(result.filteredNamespace).toBeUndefined()
        expect(result.sourceSearchQuery).toEqual({
            q: "my-query",
        })
    })

    it("prefers explicitNamespace argument over route query namespace", () => {
        const result = parseFlowsSearchQuery(
            {
                "filters[q][CONTAINS]": "my-query",
                "filters[namespace][PREFIX]": "ignored.namespace",
            },
            "explicit.namespace",
        )
        expect(result.searchQuery).toBe("my-query")
        expect(result.filteredNamespace).toBe("explicit.namespace")
        expect(result.sourceSearchQuery).toEqual({
            q: "my-query",
            namespace: "explicit.namespace",
        })
    })

    it("handles array of q values and comma-separated namespace values", () => {
        const result = parseFlowsSearchQuery({
            q: ["hello", "world"],
            "filters[namespace][IN]": "first.namespace,second.namespace",
        })
        expect(result.searchQuery).toBe("hello world")
        expect(result.filteredNamespace).toBe("first.namespace")
        expect(result.sourceSearchQuery).toEqual({
            q: "hello world",
            namespace: "first.namespace",
        })
    })
})
