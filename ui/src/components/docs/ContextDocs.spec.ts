import {afterAll, afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {createPinia, setActivePinia, type Pinia} from "pinia"
import {i18nShallowMount} from "../../../tests/unit/i18nMount"

const fetch = vi.fn<typeof globalThis.fetch>()
vi.stubGlobal("fetch", fetch)
vi.mock("../../stores/api", () => ({API_URL: "https://api.kestra.io"}))

import {useDocStore} from "../../stores/doc"
import ContextInfoContent from "../ContextInfoContent.vue"
import ContextDocs from "./ContextDocs.vue"

const PAGES: {docId?: string, title: string, parsedUrl: string}[] = [
    {title: "Kestra Docs", parsedUrl: "docs"},
    {docId: "flows", title: "Flows", parsedUrl: "docs/workflow-components/flow"},
    {docId: "kv", title: "KV Store", parsedUrl: "docs/concepts/kv-store"},
]

function respond(url: string): Response {
    const page = PAGES.find(({docId, parsedUrl}) => url.endsWith(`/docs/${parsedUrl}/versions/1.3.0`) || (docId !== undefined && url.endsWith(`/versions/1.3.0/doc/${docId}`)))
    if (!page) return new Response("", {status: 404})
    return new Response(`# ${page.title}`, {headers: {"x-kestra-metadata": JSON.stringify({title: page.title, parsedUrl: page.parsedUrl})}})
}

describe("ContextDocs", () => {
    let pinia: Pinia
    let wrapper: VueWrapper | undefined

    beforeEach(() => {
        pinia = createPinia()
        setActivePinia(pinia)
        fetch.mockReset()
        fetch.mockImplementation(async (url) => respond(String(url)))
        useDocStore().initResourceUrlTemplate("1.3.0")
    })
    afterEach(() => wrapper?.unmount())
    afterAll(() => vi.unstubAllGlobals())

    const docIdLookups = () => fetch.mock.calls.map(([url]) => String(url)).filter((url) => url.includes("/doc/"))
    const panelTitle = () => wrapper?.findComponent(ContextInfoContent).props("title")

    function openOn(docId: string) {
        useDocStore().docId = docId
        wrapper = i18nShallowMount(ContextDocs, {global: {plugins: [pinia]}})
    }

    function navigateTo(docId: string) {
        const docStore = useDocStore()
        docStore.docId = docId
        docStore.docPath = undefined
    }

    it("looks up the page's doc once when the panel opens", async () => {
        openOn("flows")
        await flushPromises()

        expect(docIdLookups()).toEqual(["https://api.kestra.io/v1/docs/versions/1.3.0/doc/flows"])
        expect(panelTitle()).toBe("Flows")
    })

    it("follows the user from a page without a doc to one with a doc while the panel is open", async () => {
        openOn("logs")
        await flushPromises()
        expect(panelTitle()).toBe("Kestra Docs")

        navigateTo("kv")
        await flushPromises()

        expect(panelTitle()).toBe("KV Store")
    })

    it("keeps the current page's doc when the previous page's lookup answers late", async () => {
        let answerFlows!: () => void
        fetch.mockImplementation((url) => String(url).endsWith("/doc/flows")
            ? new Promise((resolve) => answerFlows = () => resolve(respond(String(url))))
            : Promise.resolve(respond(String(url))))
        openOn("flows")

        navigateTo("kv")
        await flushPromises()
        answerFlows()
        await flushPromises()

        expect(panelTitle()).toBe("KV Store")
        expect(useDocStore().docPath).toBe("docs/concepts/kv-store")
    })
})
