import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, shallowMount, type VueWrapper} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia, setActivePinia, type Pinia} from "pinia"

const {get} = vi.hoisted(() => ({get: vi.fn()}))
vi.mock("axios", () => ({default: {get}}))
vi.mock("../../../../src/stores/api", () => ({API_URL: "https://api.kestra.io"}))

import {useDocStore} from "../../../../src/stores/doc"
import ContextInfoContent from "../../../../src/components/ContextInfoContent.vue"
import ContextDocs from "../../../../src/components/docs/ContextDocs.vue"

const PAGES: {docId?: string, title: string, parsedUrl: string}[] = [
    {title: "Kestra Docs", parsedUrl: "docs"},
    {docId: "flows", title: "Flows", parsedUrl: "docs/workflow-components/flow"},
    {docId: "kv", title: "KV Store", parsedUrl: "docs/concepts/kv-store"},
]

function respond(url: string): Promise<{data: string, headers: Record<string, string>}> {
    const page = PAGES.find(({docId, parsedUrl}) => url.endsWith(`/docs/${parsedUrl}/versions/1.3.0`) || (docId !== undefined && url.endsWith(`/versions/1.3.0/doc/${docId}`)))
    if (!page) return Promise.reject(Object.assign(new Error("Not found"), {response: {status: 404}}))
    return Promise.resolve({data: `# ${page.title}`, headers: {"x-kestra-metadata": JSON.stringify({title: page.title, parsedUrl: page.parsedUrl})}})
}

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {docs: "Docs"}}, missingWarn: false, fallbackWarn: false})

describe("ContextDocs", () => {
    let pinia: Pinia
    let wrapper: VueWrapper | undefined

    beforeEach(() => {
        pinia = createPinia()
        setActivePinia(pinia)
        get.mockReset()
        get.mockImplementation((url: string) => respond(url))
        useDocStore().initResourceUrlTemplate("1.3.0")
    })
    afterEach(() => wrapper?.unmount())

    const docIdLookups = () => get.mock.calls.map(([url]) => String(url)).filter((url) => url.includes("/doc/"))
    const panelTitle = () => wrapper?.findComponent(ContextInfoContent).props("title")

    function openOn(docId: string) {
        useDocStore().docId = docId
        wrapper = shallowMount(ContextDocs, {global: {plugins: [i18n, pinia]}})
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
        get.mockImplementation((url: string) => url.endsWith("/doc/flows")
            ? new Promise((resolve) => answerFlows = () => resolve(respond(url)))
            : respond(url))
        openOn("flows")

        navigateTo("kv")
        await flushPromises()
        answerFlows()
        await flushPromises()

        expect(panelTitle()).toBe("KV Store")
        expect(useDocStore().docPath).toBe("docs/concepts/kv-store")
    })
})
