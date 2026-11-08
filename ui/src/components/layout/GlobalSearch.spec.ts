import {describe, expect, it, vi, beforeEach} from "vitest"
import {nextTick, ref} from "vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

const {push} = vi.hoisted(() => ({push: vi.fn()}))

vi.mock("vue-router", async (importOriginal) => ({
    ...(await importOriginal<typeof import("vue-router")>()),
    useRouter: () => ({push}),
}))

const mockMenu = ref([
    {
        id: "workspace",
        title: "Workspace",
        child: [
            {
                id: "flows",
                title: "Flows",
                href: {name: "flows/list"},
            },
            {
                id: "executions",
                title: "Executions",
                href: {name: "executions/list"},
            },
        ],
    },
])

vi.mock("override/components/useLeftMenu", () => ({
    useLeftMenu: () => ({menu: mockMenu}),
}))

import GlobalSearch from "./GlobalSearch.vue"

describe("GlobalSearch source search item", () => {
    beforeEach(() => {
        push.mockClear()
    })

    it("appends source search entry to results when a search term is typed and navigates on click", async () => {
        const wrapper = i18nMount(GlobalSearch, {
            messages: {
                flows: "Flows",
                "source search": "Source search",
                search_in_flow_source: "Search \"{query}\" in flow source",
            },
            global: {
                stubs: {
                    teleport: true,
                    KsScrollbar: {template: "<div><slot /></div>"},
                    RouterLink: {
                        template: "<a class=\"result-link\" :data-to=\"JSON.stringify(to)\"><slot /></a>",
                        props: ["to"],
                    },
                },
            },
        })

        window.dispatchEvent(new KeyboardEvent("keydown", {key: "k", metaKey: true}))
        await nextTick()

        expect(wrapper.find(".search-overlay").exists()).toBe(true)

        const input = wrapper.find("input")
        expect(input.exists()).toBe(true)
        await input.setValue("my-query")
        await nextTick()

        const items = wrapper.findAll(".result-item")
        const sourceItem = items.find((r) => r.text().includes("my-query"))
        expect(sourceItem).toBeDefined()
        expect(sourceItem?.text()).toContain("Flows")
        expect(sourceItem?.text()).toContain("Search \"my-query\" in flow source")

        const link = sourceItem?.find(".result-link")
        expect(link?.attributes("data-to")).toBe(JSON.stringify({name: "flows/search", query: {q: "my-query"}}))
        await link?.trigger("click")
        await nextTick()

        expect(wrapper.find(".search-overlay").exists()).toBe(false)
    })

    it("shows fixed Flows / Source search entry when typing source", async () => {
        const wrapper = i18nMount(GlobalSearch, {
            messages: {
                flows: "Flows",
                "source search": "Source search",
                search_in_flow_source: "Search \"{query}\" in flow source",
            },
            global: {
                stubs: {
                    teleport: true,
                    KsScrollbar: {template: "<div><slot /></div>"},
                    RouterLink: {
                        template: "<a class=\"result-link\" :data-to=\"JSON.stringify(to)\"><slot /></a>",
                        props: ["to"],
                    },
                },
            },
        })

        window.dispatchEvent(new KeyboardEvent("keydown", {key: "k", metaKey: true}))
        await nextTick()

        const input = wrapper.find("input")
        await input.setValue("source")
        await nextTick()

        const items = wrapper.findAll(".result-item")
        const fixedEntry = items.find((r) => r.text().includes("Source search") && !r.text().includes("Search \"source\""))
        expect(fixedEntry).toBeDefined()
        expect(fixedEntry?.text()).toContain("Flows")
        expect(fixedEntry?.text()).toContain("Source search")

        const link = fixedEntry?.find(".result-link")
        expect(link?.attributes("data-to")).toBe(JSON.stringify({name: "flows/search"}))
    })

    it("navigates to selected item when pressing Enter", async () => {
        const wrapper = i18nMount(GlobalSearch, {
            messages: {
                flows: "Flows",
                "source search": "Source search",
                search_in_flow_source: "Search \"{query}\" in flow source",
            },
            global: {
                stubs: {
                    teleport: true,
                    KsScrollbar: {template: "<div><slot /></div>"},
                    RouterLink: {
                        template: "<a class=\"result-link\" :data-to=\"JSON.stringify(to)\"><slot /></a>",
                        props: ["to"],
                    },
                },
            },
        })

        window.dispatchEvent(new KeyboardEvent("keydown", {key: "k", metaKey: true}))
        await nextTick()

        const input = wrapper.find("input")
        await input.setValue("source")
        await nextTick()

        const ksSearch = wrapper.findComponent({name: "KsSearch"})
        await ksSearch.vm.$emit("keydown", new KeyboardEvent("keydown", {key: "Enter"}))
        await nextTick()

        expect(push).toHaveBeenCalledWith({name: "flows/search"})
        expect(wrapper.find(".search-overlay").exists()).toBe(false)
    })
})
