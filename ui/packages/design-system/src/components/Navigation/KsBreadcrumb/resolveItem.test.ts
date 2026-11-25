import {describe, expect, test, vi} from "vitest"
import {RouterLink} from "vue-router"
import {resolveItem} from "./resolveItem"

describe("resolveItem", () => {
    test("resolves a disabled item to a span with no attributes, even when it also has a link", () => {
        expect(resolveItem({label: "Disabled", disabled: true, link: "/flows", onClick: vi.fn()}))
            .toEqual({tag: "span", attrs: {}})
    })

    test("resolves an item with a link to RouterLink with to set", () => {
        expect(resolveItem({label: "Flows", link: "/flows"}))
            .toEqual({tag: RouterLink, attrs: {to: "/flows"}})
    })

    test("resolves an item with only an onClick to an anchor with href=\"#\"", () => {
        const result = resolveItem({label: "Action", onClick: vi.fn()})

        expect(result.tag).toBe("a")
        expect(result.attrs.href).toBe("#")
        expect(result.attrs.onClick).toBeTypeOf("function")
    })

    test("calls preventDefault and then the item's own onClick when the handler runs", () => {
        const calls: string[] = []
        const preventDefault = vi.fn(() => calls.push("preventDefault"))
        const onClick = vi.fn(() => calls.push("onClick"))
        const result = resolveItem({label: "Action", onClick})
        const handler = result.attrs.onClick as (event: Event) => void

        handler({preventDefault} as unknown as Event)

        expect(preventDefault).toHaveBeenCalledOnce()
        expect(onClick).toHaveBeenCalledOnce()
        expect(calls).toEqual(["preventDefault", "onClick"])
    })

    test("resolves an item with neither link nor onClick to a plain span", () => {
        expect(resolveItem({label: "Current"}))
            .toEqual({tag: "span", attrs: {}})
    })

    test("prefers link over onClick when both are present", () => {
        const onClick = vi.fn()

        expect(resolveItem({label: "Both", link: "/flows", onClick}))
            .toEqual({tag: RouterLink, attrs: {to: "/flows"}})
    })
})

