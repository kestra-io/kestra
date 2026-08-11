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
                cannot_select_read_only: "Cannot select — you lack edit permission on {namespace} / {id}",
                match_count: "{count} match | {count} matches",
                open_flow: "Open flow",
                read_only: "Read-only",
                read_only_tooltip: "You have read access but not edit access on this namespace",
                replace_all_in_flow: "Replace all in flow",
                replace_this_match: "Replace this match",
                select_all_in_flow: "Select all matches in {namespace} / {id}",
                select_match: "Select match on line {line}",
            },
        },
    },
})

const globalConfig = {
    plugins: [i18n, KestraDesignSystem],
}

const makeResult = (namespace: string, id: string, snippets: string[], editable = true) => ({
    namespace,
    id,
    editable,
    matches: snippets.map((snippet, index) => ({line: (index + 1) * 10, column: 0, snippet})),
})

function mountResults(props: Partial<InstanceType<typeof SourceSearchResults>["$props"]> & {results: any[]}) {
    return mount(SourceSearchResults, {
        props: {
            selectedKey: null,
            replaceMode: false,
            selectedMatchKeys: new Set<string>(),
            ...props,
        },
        global: globalConfig,
    })
}

describe("SourceSearchResults", () => {
    test("renders a group for each result", async () => {
        const results = [
            makeResult("company.data", "flow-one", ["line [mark]match[/mark] here"]),
            makeResult("company.data", "flow-two", ["another [mark]result[/mark]"]),
        ]

        const wrapper = mountResults({results})
        await flushPromises()
        const headers = wrapper.findAll("[data-test='source-search-group-header']")
        expect(headers.length).toBe(2)
    })

    test("emits select with namespace, id and line when a group header is clicked", async () => {
        const results = [makeResult("company.data", "my-flow", ["fragment [mark]hit[/mark]"])]

        const wrapper = mountResults({results})
        await flushPromises()

        const header = wrapper.find("[data-test='source-search-group-header']")
        await header.trigger("click")

        const emitted = wrapper.emitted("select")
        expect(emitted).toBeTruthy()
        expect(emitted![0][0]).toEqual({namespace: "company.data", id: "my-flow", line: 10, column: 0})
    })

    test("emits select with the correct line when a specific match is clicked", async () => {
        const results = [makeResult("ns", "flow-id", ["line [mark]term[/mark]", "second [mark]term[/mark]", "third [mark]term[/mark]"])]

        const wrapper = mountResults({results})
        await flushPromises()

        const matches = wrapper.findAll("[data-test='source-search-match']")
        expect(matches.length).toBe(3)

        await matches[1].trigger("click")
        const emitted = wrapper.emitted("select")
        expect(emitted).toBeTruthy()
        expect(emitted![0][0]).toEqual({namespace: "ns", id: "flow-id", line: 20, column: 0})

        await matches[2].trigger("click")
        expect(wrapper.emitted("select")![1][0]).toEqual({namespace: "ns", id: "flow-id", line: 30, column: 0})
    })

    test("applies selected class only to the matching row", async () => {
        const results = [makeResult("ns", "flow-id", ["frag-0", "frag-1", "frag-2"])]

        const wrapper = mountResults({results, selectedKey: "ns.flow-id#20:0"})
        await flushPromises()

        const matches = wrapper.findAll("[data-test='source-search-match']")
        expect(matches[0].classes()).not.toContain("result-match--selected")
        expect(matches[1].classes()).toContain("result-match--selected")
        expect(matches[2].classes()).not.toContain("result-match--selected")
    })

    test("sanitizes snippet html and renders mark tags", async () => {
        const results = [makeResult("ns", "flow-id", ["text [mark]keyword[/mark] end"])]

        const wrapper = mountResults({results})
        await flushPromises()

        const code = wrapper.find("[data-test='source-search-match'] code")
        expect(code.html()).toContain("<mark>keyword</mark>")
    })

    test("escapes html in snippet before converting mark tags", async () => {
        const results = [makeResult("ns", "flow-id", ["<script>evil()</script> [mark]safe[/mark]"])]

        const wrapper = mountResults({results})
        await flushPromises()

        const code = wrapper.find("[data-test='source-search-match'] code")
        expect(code.html()).not.toContain("<script>")
        expect(code.html()).toContain("&lt;script&gt;")
        expect(code.html()).toContain("<mark>safe</mark>")
    })

    test("renders a secret chip instead of the snippet for secret() references", async () => {
        const results = [makeResult("ns", "flow-id", ["serviceAccount: [mark]secret[/mark]('GCP_SERVICE_ACCOUNT')"])]

        const wrapper = mountResults({results})
        await flushPromises()

        expect(wrapper.text()).toContain("secret('GCP_SERVICE_ACCOUNT')")
        expect(wrapper.find(".result-match-snippet").exists()).toBe(false)
    })

    test("shows the read-only pill for non-editable flows", async () => {
        const results = [makeResult("ns", "locked-flow", ["frag"], false)]

        const wrapper = mountResults({results})
        await flushPromises()

        expect(wrapper.find(".result-group-readonly").exists()).toBe(true)
    })

    test("does not render checkboxes outside replace mode", async () => {
        const results = [makeResult("ns", "flow-id", ["frag"])]

        const wrapper = mountResults({results, replaceMode: false})
        await flushPromises()

        expect(wrapper.findAll(".kel-checkbox").length).toBe(0)
    })

    test("emits toggle-flow when the group checkbox is toggled in replace mode", async () => {
        const results = [makeResult("ns", "flow-id", ["frag-0", "frag-1"])]

        const wrapper = mountResults({results, replaceMode: true})
        await flushPromises()

        const checkbox = wrapper.find(".result-group-checkbox input[type='checkbox']")
        await checkbox.setValue(true)

        const emitted = wrapper.emitted("toggle-flow")
        expect(emitted).toBeTruthy()
        expect(emitted![0][0]).toEqual({namespace: "ns", id: "flow-id", checked: true})
    })

    test("open flow link renders with the correct flow path in template", () => {
        const results = [makeResult("my.ns", "my-flow", ["frag"])]

        const wrapper = mountResults({results})

        expect(wrapper.html()).toContain("my.ns")
        expect(wrapper.html()).toContain("my-flow")
    })

    test("keys two occurrences on the same line distinctly by column", async () => {
        const results = [{
            namespace: "ns",
            id: "flow-id",
            editable: true,
            matches: [
                {line: 5, column: 5, snippet: "msg: [mark]dup[/mark] and dup"},
                {line: 5, column: 13, snippet: "msg: dup and [mark]dup[/mark]"},
            ],
        }]

        const wrapper = mountResults({results, selectedKey: "ns.flow-id#5:13"})
        await flushPromises()

        const matches = wrapper.findAll("[data-test='source-search-match']")
        expect(matches.length).toBe(2)
        expect(matches[0].classes()).not.toContain("result-match--selected")
        expect(matches[1].classes()).toContain("result-match--selected")

        await matches[0].trigger("click")
        expect(wrapper.emitted("select")![0][0]).toEqual({namespace: "ns", id: "flow-id", line: 5, column: 5})
    })

    test("exposes collapseAll and expandAll", async () => {
        const results = [makeResult("ns", "flow-id", ["frag"])]

        const wrapper = mountResults({results})
        await flushPromises()

        const vm = wrapper.vm as unknown as {collapseAll: () => void; expandAll: () => void}
        expect(() => vm.collapseAll()).not.toThrow()
        expect(() => vm.expandAll()).not.toThrow()
    })
})
