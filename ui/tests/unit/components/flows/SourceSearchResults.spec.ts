import {describe, test, expect, vi} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import SourceSearchResults from "../../../../src/components/flows/SourceSearchResults.vue"
import type {SearchResourceType, SearchStatus} from "../../../../src/utils/crossResourceSearch"
import type {NamespaceFileState, KvMatchEntry, SecretMatchEntry, ResourceGroup} from "../../../../src/stores/crossResourceSearch"
import type {SourceSearchResult} from "../../../../src/utils/sourceSearchDiff"
import en from "../../../../src/translations/en.json"

vi.mock("vue-router", () => ({
    useRouter: () => ({push: vi.fn()}),
    useRoute: () => ({query: {}, params: {}}),
    RouterLink: {
        template: "<a><slot /></a>",
        props: ["to"],
    },
}))

const i18n = createI18n({legacy: false, locale: "en", messages: en})

const globalConfig = {
    plugins: [i18n, KestraDesignSystem],
}

const makeFlowResult = (namespace: string, id: string, snippets: string[], editable = true) => ({
    namespace,
    id,
    editable,
    matches: snippets.map((snippet, index) => ({line: (index + 1) * 10, column: 0, snippet})),
})

function baseProps(overrides: Record<string, unknown> = {}) {
    return {
        query: "match",
        caseSensitive: false,
        selectedTypes: ["flows", "files", "kv", "secrets"] as SearchResourceType[],
        flowsStatus: "done" as SearchStatus,
        flowsResults: [] as SourceSearchResult[],
        filesStatus: "idle" as SearchStatus,
        filesNamespaces: [] as NamespaceFileState[],
        kvStatus: "idle" as SearchStatus,
        kvGroups: [] as ResourceGroup<KvMatchEntry>[],
        secretsStatus: "idle" as SearchStatus,
        secretsGroups: [] as ResourceGroup<SecretMatchEntry>[],
        selectedKey: null as string | null,
        replaceMode: false,
        selectedMatchKeys: new Set<string>(),
        ...overrides,
    }
}

function mountResults(overrides: Record<string, unknown> = {}) {
    return mount(SourceSearchResults, {
        props: baseProps(overrides) as InstanceType<typeof SourceSearchResults>["$props"],
        global: globalConfig,
    })
}

describe("SourceSearchResults", () => {
    test("renders a flow group for each result", async () => {
        const flowsResults = [
            makeFlowResult("company.data", "flow-one", ["line [mark]match[/mark] here"]),
            makeFlowResult("company.data", "flow-two", ["another [mark]match[/mark]"]),
        ]

        const wrapper = mountResults({flowsResults})
        await flushPromises()
        const headers = wrapper.findAll("[data-test='source-search-group-header']")
        expect(headers.length).toBe(2)
    })

    test("emits select with type flows when a group header is clicked", async () => {
        const flowsResults = [makeFlowResult("company.data", "my-flow", ["fragment [mark]hit[/mark]"])]

        const wrapper = mountResults({flowsResults})
        await flushPromises()

        const header = wrapper.find("[data-test='source-search-group-header']")
        await header.trigger("click")

        const emitted = wrapper.emitted("select")
        expect(emitted).toBeTruthy()
        expect(emitted![0][0]).toEqual({type: "flows", namespace: "company.data", id: "my-flow", line: 10, column: 0})
    })

    test("sanitizes snippet html and renders mark tags for flow matches", async () => {
        const flowsResults = [makeFlowResult("ns", "flow-id", ["text [mark]keyword[/mark] end"])]

        const wrapper = mountResults({flowsResults})
        await flushPromises()

        const code = wrapper.find("[data-test='source-search-match'] code")
        expect(code.html()).toContain("<mark>keyword</mark>")
    })

    test("escapes html in snippet before converting mark tags", async () => {
        const flowsResults = [makeFlowResult("ns", "flow-id", ["<script>evil()</script> [mark]safe[/mark]"])]

        const wrapper = mountResults({flowsResults})
        await flushPromises()

        const code = wrapper.find("[data-test='source-search-match'] code")
        expect(code.html()).not.toContain("<script>")
        expect(code.html()).toContain("&lt;script&gt;")
    })

    test("shows the read-only pill for non-editable flows", async () => {
        const flowsResults = [makeFlowResult("ns", "locked-flow", ["frag"], false)]

        const wrapper = mountResults({flowsResults})
        await flushPromises()

        expect(wrapper.find(".result-group-readonly").exists()).toBe(true)
    })

    test("does not render checkboxes outside replace mode", async () => {
        const flowsResults = [makeFlowResult("ns", "flow-id", ["frag"])]

        const wrapper = mountResults({flowsResults, replaceMode: false})
        await flushPromises()

        expect(wrapper.findAll(".kel-checkbox").length).toBe(0)
    })

    test("emits toggle-flow when the group checkbox is toggled in replace mode", async () => {
        const flowsResults = [makeFlowResult("ns", "flow-id", ["frag-0", "frag-1"])]

        const wrapper = mountResults({flowsResults, replaceMode: true})
        await flushPromises()

        const checkbox = wrapper.find(".result-group-checkbox input[type='checkbox']")
        await checkbox.setValue(true)

        const emitted = wrapper.emitted("toggle-flow")
        expect(emitted).toBeTruthy()
        expect(emitted![0][0]).toEqual({namespace: "ns", id: "flow-id", checked: true})
    })

    test("does not render a type section for a type that is idle", () => {
        const wrapper = mountResults({flowsStatus: "idle", flowsResults: []})

        expect(wrapper.find("[data-type='flows']").exists()).toBe(false)
    })

    test("does not render a type section for a type that failed entirely", () => {
        const wrapper = mountResults({kvStatus: "failed", kvGroups: []})

        expect(wrapper.find("[data-type='kv']").exists()).toBe(false)
    })

    test("skips a type section when it is not part of selectedTypes", () => {
        const flowsResults = [makeFlowResult("ns", "flow-id", ["frag"])]

        const wrapper = mountResults({flowsResults, selectedTypes: ["kv"]})

        expect(wrapper.find("[data-type='flows']").exists()).toBe(false)
    })

    test("renders namespace file paths with dimmed directory segments and highlighted matches", () => {
        const wrapper = mountResults({
            query: "extract",
            filesStatus: "done",
            filesNamespaces: [{namespace: "company.data", status: "done", paths: ["scripts/extract.py"]}],
        })

        const path = wrapper.find(".result-path")
        expect(path.find(".result-path-dir").text()).toBe("scripts/")
        expect(path.find("mark").text()).toBe("extract")
    })

    test("emits select with type files when a file row is clicked", async () => {
        const wrapper = mountResults({
            query: "extract",
            filesStatus: "done",
            filesNamespaces: [{namespace: "company.data", status: "done", paths: ["scripts/extract.py"]}],
        })

        const rows = wrapper.findAll("[data-type='files'] [data-test='source-search-match']")
        await rows[0].trigger("click")

        expect(wrapper.emitted("select")![0][0]).toEqual({type: "files", namespace: "company.data", path: "scripts/extract.py"})
    })

    test("renders a retry affordance for a failed namespace and emits retry-namespace", async () => {
        const wrapper = mountResults({
            filesStatus: "counting",
            filesNamespaces: [
                {namespace: "company.platform", status: "failed", paths: [], errorMessage: "Timed out"},
            ],
        })

        expect(wrapper.text()).toContain("company.platform")
        expect(wrapper.text()).toContain("Timed out")

        await wrapper.find(".type-fail button").trigger("click")
        expect(wrapper.emitted("retry-namespace")).toEqual([[{namespace: "company.platform"}]])
    })

    test("renders a pending row while a namespace is still being searched", () => {
        const wrapper = mountResults({
            filesStatus: "counting",
            filesNamespaces: [{namespace: "company.ml", status: "pending", paths: []}],
        })

        expect(wrapper.find(".type-pending").exists()).toBe(true)
        expect(wrapper.text()).toContain("company.ml")
    })

    test("renders kv rows grouped by namespace with highlighted keys", () => {
        const wrapper = mountResults({
            query: "bucket",
            kvStatus: "done",
            kvGroups: [{namespace: "company.data", matches: [{key: "landing-bucket", updateDate: "2026-01-01T00:00:00Z"}]}],
        })

        const key = wrapper.find("[data-type='kv'] .result-key")
        expect(key.find("mark").text()).toBe("bucket")
    })

    test("renders secrets as a locked chip and never exposes a value", () => {
        const wrapper = mountResults({
            query: "aws",
            secretsStatus: "done",
            secretsGroups: [{namespace: "company.data", matches: [{key: "aws-access-key"}]}],
        })

        const chip = wrapper.find("[data-type='secrets'] .result-secret-chip")
        expect(chip.exists()).toBe(true)
        expect(chip.find("mark").text()).toBe("aws")
    })

    test("exposes collapseAll and expandAll", async () => {
        const flowsResults = [makeFlowResult("ns", "flow-id", ["frag"])]

        const wrapper = mountResults({flowsResults})
        await flushPromises()

        const vm = wrapper.vm as unknown as {collapseAll: () => void; expandAll: () => void}
        expect(() => vm.collapseAll()).not.toThrow()
        expect(() => vm.expandAll()).not.toThrow()
    })
})
