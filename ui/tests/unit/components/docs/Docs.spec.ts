import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {nextTick, reactive} from "vue"
import {createI18n} from "vue-i18n"
import {shallowMount, flushPromises, VueWrapper} from "@vue/test-utils"

// On a fresh browser-tab boot (e.g. the docs pop-out link's target="_blank" nav),
// Docs.vue can mount before App.vue's async loadGeneralResources() has initialized
// docStore.resourceUrlTemplate. Fetching before that races and throws
// "Resource URL template not initialized" (see stores/doc.ts), leaving the page
// permanently empty. The fix: gate the fetch on resourceUrlTemplate and re-fire once
// it becomes available.
const routeParams: {path?: string} = {}
vi.mock("vue-router", () => ({
    // path/fullPath/name are only exercised by DocsLayout (used un-stubbed in the
    // MDX-cleanup test below) — the other tests only read params.path.
    useRoute: () => ({params: routeParams, path: "/main/docs", fullPath: "/main/docs", name: "docs/view"}),
}))

const fetchResource = vi.fn().mockResolvedValue({metadata: {title: "Outputs"}, content: "doc content"})
const docStoreState = reactive<{resourceUrlTemplate?: string, pageMetadata?: any}>({
    resourceUrlTemplate: undefined,
    pageMetadata: undefined,
})
vi.mock("../../../../src/stores/doc", () => ({
    useDocStore: () => Object.assign(docStoreState, {fetchResource}),
}))

import Docs from "../../../../src/components/docs/Docs.vue"

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {docs: "Docs"}}, missingWarn: false, fallbackWarn: false})

let wrapper: VueWrapper

function mountDocs() {
    wrapper = shallowMount(Docs, {global: {plugins: [i18n]}})
    return wrapper
}

describe("Docs.vue — fetch gated on resourceUrlTemplate", () => {
    beforeEach(() => {
        routeParams.path = undefined
        docStoreState.resourceUrlTemplate = undefined
        docStoreState.pageMetadata = undefined
        fetchResource.mockClear()
    })

    afterEach(() => {
        wrapper?.unmount()
    })

    it("does not fetch the doc resource while the resource URL template is not yet initialized", () => {
        mountDocs()
        expect(fetchResource).not.toHaveBeenCalled()
    })

    it("fetches the doc resource once the resource URL template becomes available", async () => {
        mountDocs()
        expect(fetchResource).not.toHaveBeenCalled()

        docStoreState.resourceUrlTemplate = "http://localhost/api/v1{path}/versions/1.0.0"
        await nextTick()

        expect(fetchResource).toHaveBeenCalledTimes(1)
    })

    it("fetches immediately when the resource URL template is already set at mount", () => {
        docStoreState.resourceUrlTemplate = "http://localhost/api/v1{path}/versions/1.0.0"
        mountDocs()

        expect(fetchResource).toHaveBeenCalledTimes(1)
    })

    // The remote docs API keys every resource under its own "docs/" segment
    // (e.g. "docs/installation"), but the route "/docs/:path" already consumes
    // one "docs" segment for pretty URLs, so route.params.path never carries it.
    // fetchResource must re-add the "docs/" prefix or every lookup 404s.
    it("re-adds the docs/ prefix stripped by the route when fetching a sub-page", () => {
        routeParams.path = "installation"
        docStoreState.resourceUrlTemplate = "http://localhost/api/v1{path}/versions/1.0.0"
        mountDocs()

        expect(fetchResource).toHaveBeenCalledWith("/docs/installation")
    })

    it("fetches the docs/ root resource when there is no route path", () => {
        docStoreState.resourceUrlTemplate = "http://localhost/api/v1{path}/versions/1.0.0"
        mountDocs()

        expect(fetchResource).toHaveBeenCalledWith("/docs")
    })

    // A bare <template> with no v-if/v-for/v-slot directive is not stripped by the
    // Vue compiler and renders as a literal, inert HTML <template> element (never
    // laid out or painted), silently hiding KsMarkdown's content in the browser.
    it("does not wrap the markdown content in a bare template element", () => {
        docStoreState.resourceUrlTemplate = "http://localhost/api/v1{path}/versions/1.0.0"
        mountDocs()

        expect(wrapper.html()).not.toContain("<template")
    })

    // The remote MDX source files carry a leftover `import ChildCard from "...astro"`
    // line meant for the Astro-based docs site, not for KsMarkdown; ContextDocs.vue
    // (the embedded "?" panel) already strips it, Docs.vue (the standalone page) must too.
    it("strips leftover MDX import lines from fetched content before rendering", async () => {
        fetchResource.mockResolvedValueOnce({
            metadata: {title: "Installation"},
            content: "import ChildCard from \"~/components/docs/ChildCard.astro\"\n\nHello world",
        })
        docStoreState.resourceUrlTemplate = "http://localhost/api/v1{path}/versions/1.0.0"
        // DocsLayout renders its #content slot directly (no v-if guard), so unstubbing
        // just it lets KsMarkdown's stub (still shallow) receive the real content prop.
        wrapper = shallowMount(Docs, {global: {plugins: [i18n], stubs: {DocsLayout: false}}})
        await flushPromises()

        const markdown = wrapper.find("[content]")
        expect(markdown.attributes("content")).not.toContain("ChildCard.astro")
        expect(markdown.attributes("content")).toContain("Hello world")
    })
})
