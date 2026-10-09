import {describe, it, expect, vi, beforeEach} from "vitest"
import {defineComponent, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import {createPinia} from "pinia"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const getBlueprintTags = vi.fn().mockResolvedValue([])
const getBlueprints = vi.fn().mockResolvedValue({results: [], total: 0})

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {}, query: {"filters[q][EQUALS]": "slack"}}),
    useRouter: () => ({push: vi.fn()}),
}))

vi.mock("../../../stores/blueprints", () => ({
    useBlueprintsStore: () => ({getBlueprintTags, getBlueprints}),
}))

vi.mock("../../../stores/plugins", () => ({
    usePluginsStore: () => ({fetchIcons: vi.fn(), icons: {}, loadIcon: vi.fn()}),
}))

vi.mock("../../../composables/useBlueprintPlugins", () => ({
    useBlueprintPlugins: () => ({ensureInstalledPluginsLoaded: vi.fn()}),
}))

vi.mock("../../../composables/useRestoreUrl", () => ({
    default: () => ({loadInit: ref(true)}),
}))

import BlueprintsBrowser from "./BlueprintsBrowser.vue"

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
        i18nMount(BlueprintsBrowser, {global: {plugins: [createPinia()], stubs: {KsDataTable}}})
        await flushPromises()

        expect(getBlueprintTags).toHaveBeenCalledWith(expect.objectContaining({params: {q: "slack"}}))
    })
})
