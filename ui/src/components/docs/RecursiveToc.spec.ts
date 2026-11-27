import {describe, expect, it} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createMemoryHistory, createRouter} from "vue-router"
import {KsCollapseItem} from "@kestra-io/design-system"
import {i18nMount} from "../../../tests/unit/i18nMount"
import "../../utils/global"
import RecursiveToc from "./RecursiveToc.vue"
import {DISABLED_PAGES} from "./docsUtils"

interface TocChild {
    path: string
    title: string
    sidebarTitle?: string
    children?: TocChild[]
}

function mountRecursiveToc(props: {
    parent: {
        children: TocChild[]
    }
    depth?: number
    makeIndexNavigable?: boolean
    activePath?: string
}) {
    return i18nMount(RecursiveToc, {props})
}

const nestedChildren: TocChild[] = [
    {
        title: "guides",
        path: "docs/guides",
        children: [
            {
                title: "flows",
                path: "docs/guides/flows",
                children: [{title: "create-flow", path: "docs/guides/flows/create-flow"}],
            },
        ],
    },
    {
        title: "concepts",
        path: "docs/concepts",
        children: [{title: "secret", path: "docs/concepts/secret"}],
    },
]

const expandedSections = (wrapper: ReturnType<typeof mountRecursiveToc>) =>
    wrapper.findAll("[aria-expanded=\"true\"]").map((header) => header.text())

describe("RecursiveToc", () => {
    it("marks the current page of the current tenant as the active link", async () => {
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{name: "docs/view", path: "/:tenant?/docs/:path(.*)?", component: {render: () => null}}],
        })
        await router.push("/main/docs/concepts/secret")

        const wrapper = i18nMount(RecursiveToc, {
            props: {parent: {children: [{title: "secret", path: "docs/concepts/secret"}, {title: "revision", path: "docs/concepts/revision"}]}},
            global: {plugins: [router], stubs: {RouterLink: false}},
        })

        const links = wrapper.findAll("a")
        expect(links.map((link) => link.attributes("href"))).toEqual(["/main/docs/concepts/secret", "/main/docs/concepts/revision"])
        expect(links[0].attributes("aria-current")).toBe("page")
        expect(links[1].attributes("aria-current")).toBeUndefined()
    })

    it("expands every section holding the active page and follows it on navigation", async () => {
        const wrapper = mountRecursiveToc({parent: {children: nestedChildren}, activePath: "docs/guides/flows/create-flow"})
        await flushPromises()
        expect(expandedSections(wrapper)).toEqual(["Guides", "Flows"])

        await wrapper.setProps({activePath: "docs/concepts/secret"})
        await flushPromises()
        expect(expandedSections(wrapper)).toEqual(["Concepts"])
    })

    it("renders a flat list of children as links", () => {
        const wrapper = mountRecursiveToc({
            parent: {
                children: [
                    {title: "overview", path: "docs/overview"},
                    {title: "quickstart", path: "docs/quickstart"},
                    {title: "architecture", sidebarTitle: "System Architecture", path: "docs/architecture"},
                ],
            },
        })

        const links = wrapper.findAll("a")
        expect(links).toHaveLength(3)
        expect(links[0].text()).toBe("Overview")
        expect(links[1].text()).toBe("Quickstart")
        expect(links[2].text()).toBe("System Architecture")

        const routerLinks = wrapper.findAllComponents({name: "RouterLink"})
        expect(routerLinks).toHaveLength(3)
        expect(routerLinks[0].props("to")).toEqual({path: "/docs/overview"})
        expect(routerLinks[1].props("to")).toEqual({path: "/docs/quickstart"})
        expect(routerLinks[2].props("to")).toEqual({path: "/docs/architecture"})

        expect(wrapper.findAllComponents(KsCollapseItem)).toHaveLength(0)
    })

    it("renders a collapsible section for a child with children and recurses into it", () => {
        const wrapper = mountRecursiveToc({
            parent: {
                children: [
                    {
                        title: "guides",
                        path: "docs/guides",
                        children: [
                            {title: "create-flow", path: "docs/guides/create-flow"},
                        ],
                    },
                ],
            },
        })

        const collapseItems = wrapper.findAllComponents(KsCollapseItem)
        expect(collapseItems).toHaveLength(1)
        expect(collapseItems[0].props("name")).toBe("docs/guides")

        const routerLinks = wrapper.findAllComponents({name: "RouterLink"})
        expect(routerLinks).toHaveLength(2)
        expect(routerLinks[0].props("to")).toEqual({path: "/docs/guides"})
        expect(routerLinks[0].text()).toBe("Guides")
        expect(routerLinks[1].props("to")).toEqual({path: "/docs/guides/create-flow"})
        expect(routerLinks[1].text()).toBe("Create-flow")
    })

    it("renders every level when nested several levels deep", () => {
        const wrapper = mountRecursiveToc({
            parent: {
                children: [
                    {
                        title: "level-0",
                        path: "docs/level-0",
                        children: [
                            {
                                title: "level-1",
                                path: "docs/level-0/level-1",
                                children: [
                                    {
                                        title: "level-2",
                                        path: "docs/level-0/level-1/level-2",
                                        children: [
                                            {title: "level-3", path: "docs/level-0/level-1/level-2/level-3"},
                                        ],
                                    },
                                ],
                            },
                        ],
                    },
                ],
            },
        })

        expect(wrapper.findAllComponents(KsCollapseItem)).toHaveLength(3)

        const routerLinks = wrapper.findAllComponents({name: "RouterLink"})
        expect(routerLinks).toHaveLength(4)
        expect(routerLinks.map((link) => link.text())).toEqual([
            "Level-0",
            "Level-1",
            "Level-2",
            "Level-3",
        ])
        expect(routerLinks.map((link) => link.props("to"))).toEqual([
            {path: "/docs/level-0"},
            {path: "/docs/level-0/level-1"},
            {path: "/docs/level-0/level-1/level-2"},
            {path: "/docs/level-0/level-1/level-2/level-3"},
        ])
    })

    it("renders a page listed in DISABLED_PAGES as text rather than a link", () => {
        const disabledPath = DISABLED_PAGES[0]
        const wrapper = mountRecursiveToc({
            parent: {
                children: [
                    {
                        title: "api reference",
                        path: disabledPath,
                        children: [
                            {title: "endpoints", path: `${disabledPath}/endpoints`},
                        ],
                    },
                ],
            },
        })

        const collapseItem = wrapper.findComponent(KsCollapseItem)
        expect(collapseItem.exists()).toBe(true)

        const spans = wrapper.findAll("span")
        const disabledTitleSpan = spans.find((s) => s.text() === "Api reference")
        expect(disabledTitleSpan?.exists()).toBe(true)

        const routerLinks = wrapper.findAllComponents({name: "RouterLink"})
        expect(routerLinks).toHaveLength(1)
        expect(routerLinks[0].props("to")).toEqual({path: `/${disabledPath}/endpoints`})
        expect(routerLinks.some((link) => link.props("to")?.path === `/${disabledPath}`)).toBe(false)
    })

    it("renders every index as text when makeIndexNavigable is false", () => {
        const wrapper = mountRecursiveToc({
            parent: {
                children: [
                    {
                        title: "guides",
                        path: "docs/guides",
                        children: [
                            {title: "subguide", path: "docs/guides/subguide"},
                        ],
                    },
                ],
            },
            makeIndexNavigable: false,
        })

        const disabledTitleSpan = wrapper.findAll("span").find((s) => s.text() === "Guides")
        expect(disabledTitleSpan?.exists()).toBe(true)

        const routerLinks = wrapper.findAllComponents({name: "RouterLink"})
        expect(routerLinks).toHaveLength(1)
        expect(routerLinks[0].props("to")).toEqual({path: "/docs/guides/subguide"})
        expect(routerLinks.some((link) => link.props("to")?.path === "/docs/guides")).toBe(false)
    })

    it("increases the depth class with nesting", () => {
        const wrapper = mountRecursiveToc({
            parent: {
                children: [
                    {
                        title: "level-0",
                        path: "docs/level-0",
                        children: [
                            {
                                title: "level-1",
                                path: "docs/level-0/level-1",
                                children: [
                                    {title: "level-2", path: "docs/level-0/level-1/level-2"},
                                ],
                            },
                        ],
                    },
                ],
            },
        })

        const depth0 = wrapper.find(".depth-0")
        const depth1 = wrapper.find(".depth-1")
        const depth2 = wrapper.find(".depth-2")

        expect(depth0.exists()).toBe(true)
        expect(depth0.text()).toBe("Level-0")

        expect(depth1.exists()).toBe(true)
        expect(depth1.text()).toBe("Level-1")

        expect(depth2.exists()).toBe(true)
        expect(depth2.text()).toBe("Level-2")
    })

    it("renders nothing without throwing when children list is empty", () => {
        const wrapper = mountRecursiveToc({
            parent: {
                children: [],
            },
        })

        expect(wrapper.findAll("a")).toHaveLength(0)
        expect(wrapper.findAllComponents(KsCollapseItem)).toHaveLength(0)
        expect(wrapper.text()).toBe("")
    })
})
