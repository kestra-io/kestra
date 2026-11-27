import {describe, it, expect, vi, beforeEach} from "vitest"
import {computed, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

type Route = {name: string; params: Record<string, string>; query: Record<string, string>; meta: {tab: string}}

const current = vi.hoisted(() => ({
    route: {name: "namespaces/update/overview", params: {id: "company.beta"}, query: {}, meta: {tab: "overview"}} as Route,
    replace: undefined as unknown as ReturnType<typeof vi.fn>,
}))

vi.mock("vue-router", async (importOriginal) => {
    const {reactive} = await import("vue")
    const {vi: vitest} = await import("vitest")
    current.route = reactive(current.route)
    current.replace = vitest.fn(({query}: {query: Record<string, string>}) => {
        current.route.query = query
    })
    return {...await importOriginal<typeof import("vue-router")>(), useRoute: () => current.route, useRouter: () => ({replace: current.replace})}
})

const stubs = vi.hoisted(() => ({
    make: async (name: string) => {
        const {defineComponent, h} = await import("vue")
        return {default: defineComponent({name, setup: () => () => h("div")})}
    },
}))

vi.mock("override/components/namespaces/useTabs", () => ({useTabs: () => ({tabs: ref([])})}))
vi.mock("./utils/useHelpers", () => ({useHelpers: () => ({details: computed(() => ({title: "", breadcrumb: []}))})}))
vi.mock("override/stores/namespaces", () => ({useNamespacesStore: () => ({namespace: undefined, load: vi.fn()})}))
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({configs: {}})}))
vi.mock("../layout/TopNavBar.vue", () => stubs.make("TopNavBar"))
vi.mock("override/components/namespaces/Actions.vue", () => stubs.make("Actions"))
vi.mock("../Tabs.vue", () => stubs.make("Tabs"))

import Namespace from "./Namespace.vue"

const TIME_RANGE = "filters[timeRange][EQUALS]"

describe("Namespace", () => {
    beforeEach(() => {
        current.route.params = {id: "company.beta"}
        current.route.query = {}
        current.replace.mockClear()
    })

    it("applies the default interval when switching to another namespace's overview", async () => {
        i18nMount(Namespace)
        await flushPromises()
        expect(current.route.query[TIME_RANGE]).toBeTruthy()

        current.route.params = {id: "company.alpha"}
        current.route.query = {}
        await flushPromises()

        expect(current.route.query[TIME_RANGE]).toBeTruthy()
    })
})
