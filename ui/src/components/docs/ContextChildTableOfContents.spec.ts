import {describe, expect, test} from "vitest"
import {shallowMount} from "@vue/test-utils"
import type {VNode} from "vue"
import ChildTableOfContents from "../content/ChildTableOfContents.vue"
import ContextChildTableOfContents from "./ContextChildTableOfContents.vue"
import ContextDocsLink from "./ContextDocsLink.vue"

type LinkEntry = {path: string, title: string}
type RenderLink = (entry: LinkEntry) => VNode

const entry = {path: "docs/plugins/my-plugin", title: "My Plugin"}

function getRenderLink() {
    const wrapper = shallowMount(ContextChildTableOfContents, {
        props: {pageUrl: "plugins/core", max: 2},
    })

    return wrapper.getComponent(ChildTableOfContents).props("renderLink") as RenderLink
}

describe("ContextChildTableOfContents", () => {
    test("forwards pageUrl and max to ChildTableOfContents", () => {
        const wrapper = shallowMount(ContextChildTableOfContents, {
            props: {pageUrl: "plugins/core", max: 3},
        })

        const child = wrapper.getComponent(ChildTableOfContents)

        expect(child.props("pageUrl")).toBe("plugins/core")
        expect(child.props("max")).toBe(3)
    })

    test("passes a custom link renderer to ChildTableOfContents", () => {
        const renderLink = getRenderLink()

        expect(renderLink).toBeTypeOf("function")
        expect(renderLink(entry).type).toBe(ContextDocsLink)
    })

    test("strips the leading docs/ prefix from the link path", () => {
        const link = getRenderLink()(entry)

        expect(link.type).toBe(ContextDocsLink)
        expect(link.props?.href).toBe("plugins/my-plugin")
    })

    test("sets useRaw to true on ContextDocsLink", () => {
        const link = getRenderLink()(entry)

        expect(link.props?.useRaw).toBe(true)
    })

    test("renders the entry title in the link's default slot", () => {
        const link = getRenderLink()(entry)
        const slots = link.children

        expect(slots).toHaveProperty("default")
        if (typeof slots !== "object" || slots === null || !("default" in slots) || typeof slots.default !== "function") {
            throw new Error("ContextDocsLink default slot was not provided")
        }

        expect(slots.default()).toBe(entry.title)
    })
})
