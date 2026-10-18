import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import RecursiveToc from "./RecursiveToc.vue"
import {DISABLED_PAGES} from "./docsUtils"
import "../../utils/global"

interface TocItem {
    path: string
    title: string
    sidebarTitle?: string
    children?: TocItem[]
}

const mountRecursiveToc = (
    parent: {children: TocItem[]},
    options?: {makeIndexNavigable?: boolean; depth?: number},
) =>
    mount(RecursiveToc, {
        props: {
            parent,
            depth: options?.depth,
            makeIndexNavigable: options?.makeIndexNavigable,
        },
        global: {
            stubs: {
                KsCollapse: {
                    template: "<div><slot /></div>",
                },
                KsCollapseItem: {
                    props: ["name"],
                    template: "<div class=\"collapse-item\"><slot name=\"title\" /><slot /></div>",
                },
                RouterLink: {
                    props: ["to"],
                    template: "<a :href=\"to?.path\"><slot /></a>",
                },
            },
        },
    })

describe("RecursiveToc", () => {
    it("renders a flat list of children as links", () => {
        const flatChildren: TocItem[] = [
            {path: "docs/quickstart", title: "quickstart"},
            {path: "docs/tutorial", title: "tutorial"},
        ]

        const wrapper = mountRecursiveToc({children: flatChildren})

        const links = wrapper.findAll("a")
        expect(links).toHaveLength(2)
        expect(links[0].attributes("href")).toBe("/docs/quickstart")
        expect(links[0].text()).toBe("Quickstart")
        expect(links[1].attributes("href")).toBe("/docs/tutorial")
        expect(links[1].text()).toBe("Tutorial")
        expect(wrapper.findAll(".collapse-item")).toHaveLength(0)
    })

    it("renders a child with its own children as a collapsible section and recurses into it", () => {
        const nestedChildren: TocItem[] = [
            {
                path: "docs/parent",
                title: "parent",
                children: [
                    {path: "docs/parent/child", title: "child"},
                ],
            },
        ]

        const wrapper = mountRecursiveToc({children: nestedChildren})

        const collapseItems = wrapper.findAll(".collapse-item")
        expect(collapseItems).toHaveLength(1)

        const links = wrapper.findAll("a")
        expect(links).toHaveLength(2)
        expect(links[0].attributes("href")).toBe("/docs/parent")
        expect(links[0].text()).toBe("Parent")
        expect(links[1].attributes("href")).toBe("/docs/parent/child")
        expect(links[1].text()).toBe("Child")
    })

    it("renders every level when nesting several levels deep", () => {
        const multiLevelChildren: TocItem[] = [
            {
                path: "docs/level1",
                title: "level1",
                children: [
                    {
                        path: "docs/level1/level2",
                        title: "level2",
                        children: [
                            {path: "docs/level1/level2/level3", title: "level3"},
                        ],
                    },
                ],
            },
        ]

        const wrapper = mountRecursiveToc({children: multiLevelChildren})

        const collapseItems = wrapper.findAll(".collapse-item")
        expect(collapseItems).toHaveLength(2)

        const links = wrapper.findAll("a")
        expect(links).toHaveLength(3)
        expect(links[0].attributes("href")).toBe("/docs/level1")
        expect(links[1].attributes("href")).toBe("/docs/level1/level2")
        expect(links[2].attributes("href")).toBe("/docs/level1/level2/level3")
    })

    it("renders a page listed in DISABLED_PAGES as text rather than a link", () => {
        const disabledPath = DISABLED_PAGES[0]
        const children: TocItem[] = [
            {
                path: disabledPath,
                title: "api reference",
                children: [
                    {path: `${disabledPath}/endpoint`, title: "endpoint"},
                ],
            },
        ]

        const wrapper = mountRecursiveToc({children})

        const span = wrapper.find("span.depth-0")
        expect(span.exists()).toBe(true)
        expect(span.text()).toBe("Api reference")

        // The parent is rendered as a span, only the child has a link
        const links = wrapper.findAll("a")
        expect(links).toHaveLength(1)
        expect(links[0].attributes("href")).toBe(`/${disabledPath}/endpoint`)
    })

    it("renders every index as text when makeIndexNavigable is false", () => {
        const children: TocItem[] = [
            {
                path: "docs/section",
                title: "section",
                children: [
                    {path: "docs/section/child", title: "child"},
                ],
            },
        ]

        const wrapper = mountRecursiveToc({children}, {makeIndexNavigable: false})

        const span = wrapper.find("span.depth-0")
        expect(span.exists()).toBe(true)
        expect(span.text()).toBe("Section")

        // Parent is rendered as text, child remains a link
        const links = wrapper.findAll("a")
        expect(links).toHaveLength(1)
        expect(links[0].attributes("href")).toBe("/docs/section/child")
    })

    it("increases the depth class with nesting", () => {
        const children: TocItem[] = [
            {
                path: "docs/root",
                title: "root",
                children: [
                    {
                        path: "docs/root/sub",
                        title: "sub",
                        children: [
                            {path: "docs/root/sub/leaf", title: "leaf"},
                        ],
                    },
                ],
            },
        ]

        const wrapper = mountRecursiveToc({children})

        expect(wrapper.find(".depth-0").exists()).toBe(true)
        expect(wrapper.find(".depth-1").exists()).toBe(true)
        expect(wrapper.find(".depth-2").exists()).toBe(true)
    })

    it("renders nothing without throwing when children list is empty", () => {
        expect(() => {
            const wrapper = mountRecursiveToc({children: []})
            expect(wrapper.findAll("a")).toHaveLength(0)
            expect(wrapper.findAll(".collapse-item")).toHaveLength(0)
        }).not.toThrow()
    })
})
