import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {
    buildSubflowLinks,
    createFlowExistenceChecker,
    createSubflowLinkOpener,
    decodeSubflowTarget,
    encodeSubflowTarget,
    filterExistingSubflowLinks,
    SUBFLOW_LINK_SCHEME,
} from "../../../src/composables/monaco/languages/subflowLinkProvider"

const SUBFLOW_SOURCE = [
    "id: parent",
    "namespace: company.team",
    "",
    "tasks:",
    "  - id: call_subflow",
    "    type: io.kestra.plugin.core.flow.Subflow",
    "    namespace: other.namespace",
    "    flowId: child_flow",
].join("\n")

function fakeModel(value: string) {
    return {
        getValue: () => value,
        getPositionAt: (offset: number) => ({lineNumber: 1, column: offset + 1}),
    }
}

describe("buildSubflowLinks", () => {
    it("returns one Monaco link on the flowId value", () => {
        const model = fakeModel(SUBFLOW_SOURCE)

        const links = buildSubflowLinks(model)

        expect(links).toHaveLength(1)
        expect(links[0].target).toEqual({namespace: "other.namespace", flowId: "child_flow"})

        const flowIdOffset = SUBFLOW_SOURCE.indexOf("child_flow")
        expect(links[0].range.startColumn).toBe(flowIdOffset + 1)
        expect(links[0].range.endColumn).toBe(flowIdOffset + "child_flow".length + 1)
    })

    it("returns no links for yaml without a subflow task", () => {
        const noSubflow = [
            "id: parent",
            "namespace: company.team",
            "tasks:",
            "  - id: log",
            "    type: io.kestra.plugin.core.log.Log",
            "    message: hi",
        ].join("\n")

        expect(buildSubflowLinks(fakeModel(noSubflow))).toEqual([])
    })
})

describe("decodeSubflowTarget (link activation trust boundary)", () => {
    it("round-trips a valid target", () => {
        const target = {namespace: "ns", flowId: "f"}
        expect(decodeSubflowTarget(encodeSubflowTarget(target))).toEqual(target)
    })

    it("returns undefined on malformed query", () => {
        expect(decodeSubflowTarget("%%%not-json")).toBeUndefined()
    })

    it("returns undefined on JSON null", () => {
        expect(decodeSubflowTarget(encodeURIComponent("null"))).toBeUndefined()
    })

    it("returns undefined when flowId is missing", () => {
        expect(decodeSubflowTarget(encodeURIComponent(JSON.stringify({namespace: "ns"})))).toBeUndefined()
    })

    it("returns undefined when flowId is not a string", () => {
        expect(decodeSubflowTarget(encodeURIComponent(JSON.stringify({namespace: "ns", flowId: 5})))).toBeUndefined()
    })

    it("keeps only namespace and flowId, dropping extra keys", () => {
        const query = encodeURIComponent(JSON.stringify({namespace: "ns", flowId: "f", evil: 1}))
        expect(decodeSubflowTarget(query)).toEqual({namespace: "ns", flowId: "f"})
    })
})

describe("createSubflowLinkOpener", () => {
    let openSpy: ReturnType<typeof vi.spyOn>

    function fakeRouter() {
        return {
            currentRoute: {value: {params: {tenant: "main"}}},
            resolve: vi.fn(() => ({href: "/resolved"})),
        }
    }

    beforeEach(() => {
        openSpy = vi.spyOn(window, "open").mockImplementation(() => null)
    })

    afterEach(() => {
        openSpy.mockRestore()
    })

    it("ignores foreign schemes without navigating", () => {
        const router = fakeRouter()
        const opener = createSubflowLinkOpener(router as any)

        expect(opener.open({scheme: "https", query: ""})).toBe(false)
        expect(router.resolve).not.toHaveBeenCalled()
        expect(openSpy).not.toHaveBeenCalled()
    })

    it("returns false when the query cannot be decoded", () => {
        const router = fakeRouter()
        const opener = createSubflowLinkOpener(router as any)

        expect(opener.open({scheme: SUBFLOW_LINK_SCHEME, query: "%%%"})).toBe(false)
        expect(openSpy).not.toHaveBeenCalled()
    })

    it("opens the referenced flow on its edit tab in a new browser tab", () => {
        const router = fakeRouter()
        const opener = createSubflowLinkOpener(router as any)
        const query = encodeSubflowTarget({namespace: "ns", flowId: "f"})

        expect(opener.open({scheme: SUBFLOW_LINK_SCHEME, query})).toBe(true)
        expect(router.resolve).toHaveBeenCalledWith({
            name: "flows/update",
            params: {namespace: "ns", id: "f", tab: "edit", tenant: "main"},
        })
        expect(openSpy).toHaveBeenCalledWith("/resolved", "_blank")
    })
})

function monacoLink(namespace: string, flowId: string) {
    return {
        range: {startLineNumber: 1, startColumn: 1, endLineNumber: 1, endColumn: 2},
        target: {namespace, flowId},
    }
}

describe("createFlowExistenceChecker", () => {
    it("reports a flow that exists in its namespace", async () => {
        const exists = createFlowExistenceChecker(async (ns) => (ns === "a" ? ["x", "y"] : []))
        expect(await exists("a", "x")).toBe(true)
    })

    it("reports a flow that does not exist", async () => {
        const exists = createFlowExistenceChecker(async () => ["x"])
        expect(await exists("a", "missing")).toBe(false)
    })

    it("fetches each namespace only once (caches the lookup)", async () => {
        const fetch = vi.fn(async () => ["x"])
        const exists = createFlowExistenceChecker(fetch)

        await exists("a", "x")
        await exists("a", "missing")
        await exists("a", "x")

        expect(fetch).toHaveBeenCalledTimes(1)
    })

    it("treats a fetch failure as 'does not exist' without throwing", async () => {
        const exists = createFlowExistenceChecker(async () => {
            throw new Error("network down")
        })
        await expect(exists("a", "x")).resolves.toBe(false)
    })

    it("does not serve one cache scope's result to another", async () => {
        let scope = "tenant-a"
        const flowsByScope: Record<string, string[]> = {
            "tenant-a": ["only_in_a"],
            "tenant-b": ["only_in_b"],
        }
        const fetch = vi.fn(async () => flowsByScope[scope])
        const exists = createFlowExistenceChecker(fetch, () => scope)

        expect(await exists("shared", "only_in_a")).toBe(true)
        expect(await exists("shared", "only_in_b")).toBe(false)

        scope = "tenant-b"
        expect(await exists("shared", "only_in_b")).toBe(true)
        expect(await exists("shared", "only_in_a")).toBe(false)
    })

    it("still caches per namespace within a single scope", async () => {
        const fetch = vi.fn(async () => ["x"])
        const exists = createFlowExistenceChecker(fetch, () => "tenant-a")

        await exists("a", "x")
        await exists("a", "x")

        expect(fetch).toHaveBeenCalledTimes(1)
    })
})

describe("filterExistingSubflowLinks", () => {
    it("keeps only links whose target flow exists", async () => {
        const links = [monacoLink("a", "real"), monacoLink("a", "ghost")]

        const filtered = await filterExistingSubflowLinks(links, async (_ns, id) => id === "real")

        expect(filtered).toHaveLength(1)
        expect(filtered[0].target).toEqual({namespace: "a", flowId: "real"})
    })

    it("drops only the failing namespace, keeping links from healthy ones", async () => {
        const links = [monacoLink("bad", "x"), monacoLink("good", "y")]
        const exists = createFlowExistenceChecker(async (ns) => {
            if (ns === "bad") {
                throw new Error("namespace fetch failed")
            }
            return ["y"]
        })

        const filtered = await filterExistingSubflowLinks(links, exists)

        expect(filtered).toHaveLength(1)
        expect(filtered[0].target).toEqual({namespace: "good", flowId: "y"})
    })
})
