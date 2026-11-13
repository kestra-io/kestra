import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent, h, nextTick, reactive} from "vue"
import {i18nShallowMount} from "../../../tests/unit/i18nMount"
import {useFlowStore} from "../../stores/flow"

const {push} = vi.hoisted(() => ({push: vi.fn()}))

const RouterLinkStub = defineComponent({
    name: "RouterLink",
    props: {to: {type: Object, default: undefined}},
    setup: (_, {slots}) => () => h("a", {class: "source-search-link"}, slots.default?.()),
})

const route = reactive({
    name: "flows/list",
    params: {tenant: "main"} as Record<string, string>,
    query: {} as Record<string, string>,
    meta: {},
})

vi.mock("vue-router", async (importOriginal) => ({
    ...(await importOriginal<typeof import("vue-router")>()),
    useRoute: () => route,
    useRouter: () => ({push, replace: vi.fn(), resolve: vi.fn(() => ({href: ""}))}),
}))

import Flows from "./Flows.vue"

describe("Flows source search row", () => {
    let originalTitle: string
    let wrappers: ReturnType<typeof i18nShallowMount>[] = []

    beforeEach(() => {
        wrappers = []
        originalTitle = document.title
        localStorage.clear()
        setActivePinia(createPinia())
        push.mockClear()
    })

    afterEach(() => {
        for (const w of wrappers) {
            w.unmount()
        }
        wrappers = []
        localStorage.clear()
        document.title = originalTitle
    })

    it("does not display source-row when search query is empty", () => {
        route.query = {}
        const wrapper = i18nShallowMount(Flows, {
            props: {topbar: false},
            global: {
                stubs: {
                    KsDataTable: {template: "<div><slot name=\"top\" /><slot /></div>"},
                    RouterLink: RouterLinkStub,
                },
            },
        })
        wrappers.push(wrapper)

        expect(wrapper.find(".source-row").exists()).toBe(false)
    })

    it("displays source-row when search query is present, with count and link to flows/search", async () => {
        route.query = {
            "filters[q][CONTAINS]": "my-query",
            "filters[namespace][PREFIX]": "company.team",
        }

        const flowStore = useFlowStore()
        flowStore.total = 42

        const wrapper = i18nShallowMount(Flows, {
            props: {topbar: false},
            messages: {
                flows_matched_query: "{count} flow with \"{query}\" in the id or namespace. | {count} flows with \"{query}\" in the id or namespace.",
                search_in_flow_source: "Search \"{query}\" in flow source",
            },
            global: {
                stubs: {
                    KsDataTable: {template: "<div><slot name=\"top\" /><slot /></div>"},
                    RouterLink: RouterLinkStub,
                },
            },
        })
        wrappers.push(wrapper)

        await nextTick()

        const sourceRow = wrapper.find(".source-row")
        expect(sourceRow.exists()).toBe(true)
        expect(sourceRow.text()).toContain('42 flows with "my-query" in the id or namespace.')
        expect(sourceRow.text()).toContain('Search "my-query" in flow source')

        const link = wrapper.findComponent(RouterLinkStub)
        expect(link.props("to")).toEqual({
            name: "flows/search",
            query: {
                q: "my-query",
                namespace: "company.team",
            },
        })
    })

    it("does not forward namespace filter when comparator is NOT_IN or CONTAINS", async () => {
        route.query = {
            "filters[q][CONTAINS]": "my-query",
            "filters[namespace][NOT_IN]": "company.team",
        }

        const wrapper = i18nShallowMount(Flows, {
            props: {topbar: false},
            global: {
                stubs: {
                    KsDataTable: {template: "<div><slot name=\"top\" /><slot /></div>"},
                    RouterLink: RouterLinkStub,
                },
            },
        })
        wrappers.push(wrapper)

        await nextTick()

        const link = wrapper.findComponent(RouterLinkStub)
        expect(link.props("to")).toEqual({
            name: "flows/search",
            query: {
                q: "my-query",
            },
        })
    })
})
