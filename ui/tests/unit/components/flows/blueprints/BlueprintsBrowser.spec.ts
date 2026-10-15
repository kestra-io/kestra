import {describe, it, expect, vi, beforeEach} from "vitest"
import {defineComponent, ref} from "vue"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"

const getBlueprintTags = vi.fn().mockResolvedValue([])
const getBlueprints = vi.fn().mockResolvedValue({results: [], total: 0})

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {}, query: {"filters[q][EQUALS]": "slack"}}),
    useRouter: () => ({push: vi.fn()}),
}))

vi.mock("../../../../../src/stores/blueprints", () => ({
    useBlueprintsStore: () => ({getBlueprintTags, getBlueprints}),
}))

vi.mock("../../../../../src/stores/plugins", () => ({
    usePluginsStore: () => ({fetchIcons: vi.fn(), icons: {}, loadIcon: vi.fn()}),
}))

vi.mock("../../../../../src/composables/useBlueprintPlugins", () => ({
    useBlueprintPlugins: () => ({ensureInstalledPluginsLoaded: vi.fn()}),
}))

vi.mock("../../../../../src/composables/useRestoreUrl", () => ({
    default: () => ({loadInit: ref(true)}),
}))

import BlueprintsBrowser from "../../../../../src/components/flows/blueprints/BlueprintsBrowser.vue"
import en from "../../../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: en})

const KsDataTable = defineComponent({
    props: {loadData: {type: Function, required: true}},
    mounted() {
        this.loadData({page: 1, size: 25})
    },
    template: "<div />",
})

describe("BlueprintsBrowser", () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it("filters the tag list by the search in the URL", async () => {
        mount(BlueprintsBrowser, {global: {plugins: [i18n, KestraDesignSystem, createPinia()], stubs: {KsDataTable}}})
        await flushPromises()

        expect(getBlueprintTags).toHaveBeenCalledWith(expect.objectContaining({params: {q: "slack"}}))
    })
})
