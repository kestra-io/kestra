import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {reactive} from "vue"
import {i18nShallowMount} from "../../../tests/unit/i18nMount"

const route = reactive({
    name: "flows/list",
    params: {tenant: "main"} as Record<string, string>,
    query: {} as Record<string, string>,
    meta: {},
})

vi.mock("vue-router", async (importOriginal) => ({
    ...(await importOriginal<typeof import("vue-router")>()),
    useRoute: () => route,
    useRouter: () => ({push: vi.fn(), replace: vi.fn(), resolve: vi.fn(() => ({href: ""}))}),
}))

import Flows from "./Flows.vue"

/** Opens the Flows list the way a page refresh does: the sort is already in the URL when the table mounts. */
function tableSortOnLoad(query: Record<string, string>) {
    route.query = query
    const wrapper = i18nShallowMount(Flows, {global: {plugins: [createPinia()]}})
    return wrapper.findComponent({name: "KsDataTable"}).vm.$attrs.defaultSort
}

describe("Flows sort indicator", () => {
    let originalTitle: string

    beforeEach(() => {
        originalTitle = document.title
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.title = originalTitle
    })

    it.each([
        [{sort: "namespace:desc"}, {prop: "namespace", order: "descending"}],
        [{}, {prop: "id", order: "ascending"}],
    ])("starts the table's sort arrow from the URL sort %j", (query, expected) => {
        expect(tableSortOnLoad(query)).toEqual(expected)
    })
})
