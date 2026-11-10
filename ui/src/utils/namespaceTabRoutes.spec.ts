import {describe, expect, it} from "vitest"
import {createNamespaceTabRoutes, NAMESPACE_TAB_NAMES} from "./namespaceTabRoutes"

describe("createNamespaceTabRoutes", () => {
    it("creates one named child route per supplied tab in the supplied order", () => {
        expect(createNamespaceTabRoutes(["files", "overview", "flows"])).toEqual([
            {name: "namespaces/update/files", path: "files", meta: {tab: "files", docId: "files"}},
            {name: "namespaces/update/overview", path: "overview", meta: {tab: "overview", docId: "namespaces"}},
            {name: "namespaces/update/flows", path: "flows", meta: {tab: "flows", docId: "flows"}},
        ])
    })

    it("creates routes for every namespace tab by default", () => {
        const routes = createNamespaceTabRoutes()

        expect(routes).toHaveLength(NAMESPACE_TAB_NAMES.length)
        expect(routes.map((route) => route.path)).toEqual(NAMESPACE_TAB_NAMES)
        for (const [index, tab] of NAMESPACE_TAB_NAMES.entries()) {
            expect(routes[index]).toMatchObject({
                name: `namespaces/update/${tab}`,
                path: tab,
                meta: {tab},
            })
        }
    })

    it("returns no routes for an empty tab list", () => {
        expect(createNamespaceTabRoutes([])).toEqual([])
    })
})
