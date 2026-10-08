import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const {get} = vi.hoisted(() => ({get: vi.fn()}))

vi.mock("axios", () => ({default: {get}}))
vi.mock("../../../src/stores/api", () => ({API_URL: "https://api.kestra.io"}))

import {useDocStore} from "../../../src/stores/doc"

describe("doc store", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        get.mockReset()
    })

    /** Doc links resolve the same whether a caller passes the path with or without a leading slash. */
    it("builds resource URLs from the version template, whatever the slashes", () => {
        const store = useDocStore()
        store.initResourceUrlTemplate("1.3.0")

        expect(store.resourceUrl("plugins/core")).toBe("https://api.kestra.io/v1/docs/plugins/core/versions/1.3.0")
        expect(store.resourceUrl("/plugins/core")).toBe("https://api.kestra.io/v1/docs/plugins/core/versions/1.3.0")
        expect(store.resourceUrl(undefined, "search")).toBe("https://api.kestra.io/v1/search/versions/1.3.0")
    })

    /** The page title and URL come from a response header, not from the markdown body. */
    it("reads the page metadata from the response header", async () => {
        get.mockResolvedValue({data: "# Flows", headers: {"x-kestra-metadata": JSON.stringify({title: "Flows", parsedUrl: "/docs/flows"})}})
        const store = useDocStore()
        store.initResourceUrlTemplate("1.3.0")

        const resource = await store.fetchResource("flows")

        expect(resource).toEqual({content: "# Flows", metadata: {title: "Flows", parsedUrl: "/docs/flows"}})
    })

    /** Opening a doc by id records the page path it resolved to. */
    it("remembers the page a doc id resolved to", async () => {
        get.mockResolvedValue({data: "# Flows", headers: {"x-kestra-metadata": JSON.stringify({title: "Flows", parsedUrl: "/docs/flows"})}})
        const store = useDocStore()
        store.initResourceUrlTemplate("1.3.0")

        await store.fetchDocId("flows-intro")

        expect(get).toHaveBeenCalledWith("https://api.kestra.io/v1/docs/versions/1.3.0/doc/flows-intro")
        expect(store.docPath).toBe("/docs/flows")
    })

    /** Highlights render as text, so no markup may survive, nested or not (code-scanning alert 88). */
    it("turns a search highlight into plain text, nested tags included", async () => {
        get.mockResolvedValue({data: {results: [
            {url: "/docs/flows", title: "Flows", highlights: ["Run a <em>flow</em><br/>on a <strong>schedule</strong>"]},
            {url: "/docs/xss", title: "Nested", highlights: ["<scr<b>ipt>alert(1)</scr</b>ipt>"]},
        ]}})
        const store = useDocStore()
        store.initResourceUrlTemplate("1.3.0")

        const [plain, nested] = await store.search({q: "flow", scoredSearch: true})

        expect(plain).toEqual({parsedUrl: "/docs/flows", title: "Flows", preview: "Run a flow on a schedule"})
        expect(nested.preview).not.toMatch(/<[^>]*>/)
    })

    /** A query containing `&`, `#` or `+` must reach the search API intact. */
    it("sends the search query as typed, even with characters a URL would cut", async () => {
        get.mockResolvedValue({data: {results: []}})
        const store = useDocStore()
        store.initResourceUrlTemplate("1.3.0")

        await store.search({q: "C# & F#", scoredSearch: true})
        await store.search({q: "a+b"})

        expect(get.mock.calls[0]).toEqual(["https://api.kestra.io/v1/search/versions/1.3.0", {params: {q: "C# & F#", type: "DOCS"}}])
        expect(get.mock.calls[1]).toEqual(["https://api.kestra.io/v1/docs/versions/1.3.0/search", {params: {q: "a+b"}}])
    })
})
