import {describe, test, expect, vi} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import SourceSearchResults from "../../../../src/components/flows/SourceSearchResults.vue"

vi.mock("vue-router", () => ({
    useRouter: () => ({push: vi.fn()}),
    useRoute: () => ({query: {}, params: {}}),
    RouterLink: {
        template: "<a><slot /></a>",
        props: ["to"],
    },
}))

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            source_search: {
                match_count: "{count} match | {count} matches",
                open_flow: "Open flow",
            },
        },
    },
})

const globalConfig = {
    plugins: [i18n, KestraDesignSystem],
}

const makeResult = (namespace: string, id: string, fragments: string[]) => ({
    model: {namespace, id},
    fragments,
})

describe("SourceSearchResults", () => {
    test("renders KsEmpty when results is undefined", async () => {
        const wrapper = mount(SourceSearchResults, {
            props: {results: undefined, selectedKey: null},
            global: globalConfig,
        })
        await flushPromises()
        expect(wrapper.find("[data-test='source-search-results']").exists()).toBe(true)
        expect(wrapper.find(".kel-empty").exists() || wrapper.html().includes("empty")).toBeTruthy()
    })

    test("renders KsEmpty when results is empty array", async () => {
        const wrapper = mount(SourceSearchResults, {
            props: {results: [], selectedKey: null},
            global: globalConfig,
        })
        await flushPromises()
        expect(wrapper.find(".kel-empty").exists() || wrapper.html().includes("empty")).toBeTruthy()
    })

    test("renders a group for each result", async () => {
        const results = [
            makeResult("company.data", "flow-one", ["line [mark]match[/mark] here"]),
            makeResult("company.data", "flow-two", ["another [mark]result[/mark]"]),
        ]

        const wrapper = mount(SourceSearchResults, {
            props: {results, selectedKey: null},
            global: globalConfig,
        })
        await flushPromises()
        const headers = wrapper.findAll("[data-test='source-search-group-header']")
        expect(headers.length).toBe(2)
    })

    test("emits select with namespace, id and matchIndex 0 when a group header is clicked", async () => {
        const results = [
            makeResult("company.data", "my-flow", ["fragment [mark]hit[/mark]"]),
        ]

        const wrapper = mount(SourceSearchResults, {
            props: {results, selectedKey: null},
            global: globalConfig,
        })
        await flushPromises()

        const header = wrapper.find("[data-test='source-search-group-header']")
        await header.trigger("click")

        const emitted = wrapper.emitted("select")
        expect(emitted).toBeTruthy()
        expect(emitted![0][0]).toEqual({namespace: "company.data", id: "my-flow", matchIndex: 0})
    })

    test("emits select with correct matchIndex when a specific fragment is clicked", async () => {
        const results = [
            makeResult("ns", "flow-id", [
                "line [mark]term[/mark]",
                "second [mark]term[/mark]",
                "third [mark]term[/mark]",
            ]),
        ]

        const wrapper = mount(SourceSearchResults, {
            props: {results, selectedKey: null},
            global: globalConfig,
        })
        await flushPromises()

        const fragments = wrapper.findAll("[data-test='source-search-match']")
        expect(fragments.length).toBe(3)

        await fragments[1].trigger("click")
        const emitted = wrapper.emitted("select")
        expect(emitted).toBeTruthy()
        expect(emitted![0][0]).toEqual({namespace: "ns", id: "flow-id", matchIndex: 1})

        await fragments[2].trigger("click")
        expect(wrapper.emitted("select")![1][0]).toEqual({namespace: "ns", id: "flow-id", matchIndex: 2})
    })

    test("applies selected class only to the matching fragment row", async () => {
        const results = [
            makeResult("ns", "flow-id", ["frag-0", "frag-1", "frag-2"]),
        ]

        const wrapper = mount(SourceSearchResults, {
            props: {results, selectedKey: "ns.flow-id#1"},
            global: globalConfig,
        })
        await flushPromises()

        const fragments = wrapper.findAll("[data-test='source-search-match']")
        expect(fragments[0].classes()).not.toContain("result-fragment--selected")
        expect(fragments[1].classes()).toContain("result-fragment--selected")
        expect(fragments[2].classes()).not.toContain("result-fragment--selected")
    })

    test("sanitizes fragment html and renders mark tags", async () => {
        const results = [
            makeResult("ns", "flow-id", ["text [mark]keyword[/mark] end"]),
        ]

        const wrapper = mount(SourceSearchResults, {
            props: {results, selectedKey: null},
            global: globalConfig,
        })
        await flushPromises()

        const fragment = wrapper.find("[data-test='source-search-match'] pre")
        expect(fragment.html()).toContain("<mark>keyword</mark>")
    })

    test("escapes html in fragment before converting mark tags", async () => {
        const results = [
            makeResult("ns", "flow-id", ["<script>evil()</script> [mark]safe[/mark]"]),
        ]

        const wrapper = mount(SourceSearchResults, {
            props: {results, selectedKey: null},
            global: globalConfig,
        })
        await flushPromises()

        const fragment = wrapper.find("[data-test='source-search-match'] pre")
        expect(fragment.html()).not.toContain("<script>")
        expect(fragment.html()).toContain("&lt;script&gt;")
        expect(fragment.html()).toContain("<mark>safe</mark>")
    })

    test("applies selected state to the group header when any match in that flow is selected", async () => {
        const results = [
            makeResult("ns", "flow-a", ["frag"]),
            makeResult("ns", "flow-b", ["frag"]),
        ]

        const wrapper = mount(SourceSearchResults, {
            props: {results, selectedKey: "ns.flow-a#0"},
            global: globalConfig,
        })
        await flushPromises()

        const headers = wrapper.findAll("[data-test='source-search-group-header']")
        expect(headers[0].classes()).toContain("result-group-header--selected")
        expect(headers[1].classes()).not.toContain("result-group-header--selected")
    })

    test("open flow link renders with the correct flow path in template", () => {
        const results = [
            makeResult("my.ns", "my-flow", ["frag"]),
        ]

        const wrapper = mount(SourceSearchResults, {
            props: {results, selectedKey: null},
            global: globalConfig,
        })

        expect(wrapper.html()).toContain("my.ns")
        expect(wrapper.html()).toContain("my-flow")
    })
})
