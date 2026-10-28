import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const {get} = vi.hoisted(() => ({get: vi.fn()}))
vi.mock("axios", () => ({default: {get}}))
vi.mock("../../../src/stores/api", () => ({API_URL: "https://api.kestra.io"}))

import {useDocStore} from "../../../src/stores/doc"

const FLOWS_PAGE = {data: "# Flows", headers: {"x-kestra-metadata": JSON.stringify({title: "Flows", parsedUrl: "/docs/flows"})}}

describe("doc store", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        get.mockReset()
        get.mockResolvedValue(FLOWS_PAGE)
    })

    /** Opening a doc by id records the page path it resolved to. */
    it("remembers the page a doc id resolved to", async () => {
        const store = useDocStore()
        store.initResourceUrlTemplate("1.3.0")
        store.docId = "flows-intro"

        await store.fetchDocId("flows-intro")

        expect(String(get.mock.calls[0][0])).toBe("https://api.kestra.io/v1/docs/versions/1.3.0/doc/flows-intro")
        expect(store.docPath).toBe("/docs/flows")
    })

    /** The user navigated on while the lookup was in flight, so its answer belongs to a page they left. */
    it("ignores the page a doc id resolved to once the user has left that doc id", async () => {
        const store = useDocStore()
        store.initResourceUrlTemplate("1.3.0")
        store.docId = "kv"

        await store.fetchDocId("flows-intro")

        expect(store.docPath).toBeUndefined()
    })
})
