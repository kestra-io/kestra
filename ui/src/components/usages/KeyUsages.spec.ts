import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

const {mockSearchFlowsBySourceCode} = vi.hoisted(() => ({mockSearchFlowsBySourceCode: vi.fn()}))

vi.mock("@kestra-io/kestra-sdk/flows", () => ({searchFlowsBySourceCode: mockSearchFlowsBySourceCode}))
vi.mock("vue-router", () => ({
    useRoute: () => ({params: {tenant: "main"}}),
}))

import KeyUsages from "./KeyUsages.vue"
import {buildUsagePattern} from "../../composables/useKeyUsages"

const RouterLinkProbe = {
    props: ["to"],
    template: "<a :data-to=\"JSON.stringify(to)\"><slot /></a>",
}

const flow = (namespace: string, id: string, snippets: string[]) => ({
    namespace,
    id,
    editable: true,
    matches: snippets.map((snippet, index) => ({line: (index + 1) * 10, column: 0, snippet})),
})

const mountUsages = () => i18nMount(KeyUsages, {
    props: {target: {kind: "kv" as const, namespace: "company", key: "api_url"}},
    global: {stubs: {RouterLink: RouterLinkProbe}},
})

describe("KeyUsages", () => {
    beforeEach(() => {
        mockSearchFlowsBySourceCode.mockReset()
    })

    it("shows a loading state while the search runs and still shows the caveat", () => {
        mockSearchFlowsBySourceCode.mockReturnValue(new Promise(() => {}))

        const wrapper = mountUsages()

        expect(wrapper.find("[data-test='key-usages-loading']").exists()).toBe(true)
        expect(wrapper.find("[data-test='key-usages-caveat']").exists()).toBe(true)
        expect(wrapper.find("[data-test='key-usages-empty']").exists()).toBe(false)
    })

    it("renders one row per usage with flow link, line and highlighted key", async () => {
        mockSearchFlowsBySourceCode.mockResolvedValue({
            results: [flow("company.team", "daily", ["  url: \"{{ [mark]kv('api_url'[/mark]) }}\""])],
            total: 1,
        })

        const wrapper = mountUsages()
        await flushPromises()

        const rows = wrapper.findAll("[data-test='key-usages-row']")
        expect(rows).toHaveLength(1)
        expect(rows[0].text()).toContain("company.team / daily")
        expect(rows[0].text()).toContain("key_usages.line")
        expect(rows[0].find("mark").text()).toBe("api_url")
        expect(JSON.parse(rows[0].find("[data-test='key-usages-flow']").attributes("data-to")!)).toEqual({
            name: "flows/update/edit",
            params: {tenant: "main", namespace: "company.team", id: "daily"},
        })
        expect(wrapper.find("[data-test='key-usages-truncated']").exists()).toBe(false)
    })

    it("hides flows from unrelated namespaces", async () => {
        mockSearchFlowsBySourceCode.mockResolvedValue({
            results: [flow("elsewhere", "other", ["kv('api_url')"])],
            total: 1,
        })

        const wrapper = mountUsages()
        await flushPromises()

        expect(wrapper.find("[data-test='key-usages-empty']").exists()).toBe(true)
        expect(wrapper.findAll("[data-test='key-usages-row']")).toHaveLength(0)
    })

    it("shows the empty state together with the caveat", async () => {
        mockSearchFlowsBySourceCode.mockResolvedValue({results: [], total: 0})

        const wrapper = mountUsages()
        await flushPromises()

        expect(wrapper.find("[data-test='key-usages-empty']").exists()).toBe(true)
        expect(wrapper.find("[data-test='key-usages-caveat']").exists()).toBe(true)
    })

    it("shows an error without hiding the caveat or the source search link", async () => {
        mockSearchFlowsBySourceCode.mockRejectedValue(new Error("boom"))

        const wrapper = mountUsages()
        await flushPromises()

        expect(wrapper.find("[data-test='key-usages-error']").exists()).toBe(true)
        expect(wrapper.find("[data-test='key-usages-empty']").exists()).toBe(false)
        expect(wrapper.find("[data-test='key-usages-caveat']").exists()).toBe(true)
        expect(wrapper.find("[data-test='key-usages-source-search']").exists()).toBe(true)
    })

    it("says so when the results are truncated", async () => {
        mockSearchFlowsBySourceCode.mockResolvedValue({results: [flow("company", "f", ["kv('api_url')"])], total: 300})

        const wrapper = mountUsages()
        await flushPromises()

        expect(wrapper.find("[data-test='key-usages-truncated']").exists()).toBe(true)
    })

    it("links to the source search with the regex prefilled", async () => {
        mockSearchFlowsBySourceCode.mockResolvedValue({results: [], total: 0})

        const wrapper = mountUsages()
        await flushPromises()

        expect(JSON.parse(wrapper.find("[data-test='key-usages-source-search']").attributes("data-to")!)).toEqual({
            name: "flows/search",
            params: {tenant: "main"},
            query: {q: buildUsagePattern({kind: "kv", key: "api_url"}), regex: "true", types: "flows"},
        })
    })
})
