import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {nextTick, reactive} from "vue"
import {createI18n} from "vue-i18n"
import {shallowMount, VueWrapper} from "@vue/test-utils"

// Same fresh-tab-boot race as Docs.vue: Toc.vue used to fetch its sidebar structure
// onMounted, which can fire before docStore.resourceUrlTemplate is initialized and
// throw "Resource URL template not initialized". Gate on the template instead.
const children = vi.fn().mockResolvedValue({})
const docStoreState = reactive<{resourceUrlTemplate?: string}>({resourceUrlTemplate: undefined})
vi.mock("../../../../src/stores/doc", () => ({
    useDocStore: () => Object.assign(docStoreState, {children, search: vi.fn()}),
}))

import Toc from "../../../../src/components/docs/Toc.vue"

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {search: "Search"}}, missingWarn: false, fallbackWarn: false})

let wrapper: VueWrapper

function mountToc() {
    wrapper = shallowMount(Toc, {global: {plugins: [i18n], stubs: {KsAutocomplete: true}}})
    return wrapper
}

describe("Toc.vue — children fetch gated on resourceUrlTemplate", () => {
    beforeEach(() => {
        docStoreState.resourceUrlTemplate = undefined
        children.mockClear()
    })

    afterEach(() => {
        wrapper?.unmount()
    })

    it("does not fetch the doc tree while the resource URL template is not yet initialized", () => {
        mountToc()
        expect(children).not.toHaveBeenCalled()
    })

    it("fetches the doc tree once the resource URL template becomes available", async () => {
        mountToc()
        expect(children).not.toHaveBeenCalled()

        docStoreState.resourceUrlTemplate = "http://localhost/api/v1{path}/versions/1.0.0"
        await nextTick()

        expect(children).toHaveBeenCalledTimes(1)
    })

    it("fetches immediately when the resource URL template is already set at mount", () => {
        docStoreState.resourceUrlTemplate = "http://localhost/api/v1{path}/versions/1.0.0"
        mountToc()

        expect(children).toHaveBeenCalledTimes(1)
    })
})
