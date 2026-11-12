import {describe, it, expect, vi} from "vitest"
import {computed, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

type Route = {name: string; params: Record<string, string>; query: Record<string, string>; meta: {tab: string}}

const current = vi.hoisted(() => ({
    route: {name: "namespaces/update/edit", params: {id: "company"}, query: {}, meta: {tab: "edit"}} as Route,
}))

vi.mock("vue-router", async (importOriginal) => {
    const {reactive} = await import("vue")
    current.route = reactive(current.route)
    return {...await importOriginal<typeof import("vue-router")>(), useRoute: () => current.route, useRouter: () => ({replace: () => Promise.resolve()})}
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
vi.mock("../Tabs.vue", async () => {
    const {defineComponent, h} = await import("vue")
    return {
        default: defineComponent({
            name: "Tabs",
            props: {namespace: {type: String, default: undefined}},
            setup: (props) => {
                const openedOn = props.namespace
                return () => h("div", {"data-test": "tabs"}, openedOn ?? "creation")
            },
        }),
    }
})

import Namespace from "./Namespace.vue"

describe("Namespace", () => {
    it("should open the creation page on fresh tabs rather than on those of the namespace it was reached from", async () => {
        const wrapper = i18nMount(Namespace)
        await flushPromises()
        expect(wrapper.get("[data-test=tabs]").text()).toBe("company")

        current.route.name = "namespaces/create"
        current.route.params = {}
        await flushPromises()

        expect(wrapper.get("[data-test=tabs]").text()).toBe("creation")
    })
})
