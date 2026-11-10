import {describe, it, expect, vi, afterEach} from "vitest"
import {openCollapsedGroups, scrollThenFocus, useFieldNavigation, type NavFrame} from "./useFieldNavigation"

const frame = (path: string): NavFrame => ({path, label: path, schema: {}})

describe("useFieldNavigation", () => {
    it("starts empty", () => {
        const nav = useFieldNavigation()
        expect(nav.stack.value).toEqual([])
        expect(nav.current.value).toBeUndefined()
    })

    it("push tracks current and depth", () => {
        const nav = useFieldNavigation()
        nav.push(frame("inputs"))
        nav.push(frame("inputs.0"))
        expect(nav.stack.value).toHaveLength(2)
        expect(nav.current.value?.path).toBe("inputs.0")
    })

    it("pop removes the top frame", () => {
        const nav = useFieldNavigation()
        nav.push(frame("a"))
        nav.push(frame("b"))
        nav.pop()
        expect(nav.stack.value).toHaveLength(1)
        expect(nav.current.value?.path).toBe("a")
    })

    it("popTo slices to the given depth", () => {
        const nav = useFieldNavigation()
        nav.push(frame("a"))
        nav.push(frame("b"))
        nav.push(frame("c"))
        nav.popTo(0)
        expect(nav.stack.value.map(f => f.path)).toEqual(["a"])
    })

    it("reset clears the stack", () => {
        const nav = useFieldNavigation()
        nav.push(frame("a"))
        nav.reset()
        expect(nav.stack.value).toEqual([])
        expect(nav.current.value).toBeUndefined()
    })
})

describe("scrollThenFocus", () => {
    afterEach(() => {
        vi.unstubAllGlobals()
        document.body.innerHTML = ""
    })

    it("waits for the smooth scroll to settle before focusing, so focus never cuts the scroll short", () => {
        document.body.innerHTML = "<div id='field'><input id='inp' /></div>"
        const el = document.getElementById("field") as HTMLElement
        const input = document.getElementById("inp") as HTMLInputElement
        el.scrollIntoView = vi.fn()
        input.focus = vi.fn()

        const rafCallbacks: FrameRequestCallback[] = []
        vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) => {
            rafCallbacks.push(cb)
            return rafCallbacks.length
        })

        scrollThenFocus(el)

        expect(el.scrollIntoView).toHaveBeenCalledWith({behavior: "smooth", block: "center"})
        expect(input.focus).not.toHaveBeenCalled()

        while (rafCallbacks.length && (input.focus as ReturnType<typeof vi.fn>).mock.calls.length === 0) {
            rafCallbacks.shift()!(0)
        }

        expect(input.focus).toHaveBeenCalledWith({preventScroll: true})
    })

    it("stops the rAF loop once the target is unmounted, instead of focusing a detached node", () => {
        document.body.innerHTML = "<div id='field'><input id='inp' /></div>"
        const el = document.getElementById("field") as HTMLElement
        const input = document.getElementById("inp") as HTMLInputElement
        el.scrollIntoView = vi.fn()
        input.focus = vi.fn()

        const rafCallbacks: FrameRequestCallback[] = []
        vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) => {
            rafCallbacks.push(cb)
            return rafCallbacks.length
        })

        scrollThenFocus(el)
        el.remove()

        while (rafCallbacks.length) {
            rafCallbacks.shift()!(0)
        }

        expect(input.focus).not.toHaveBeenCalled()
    })
})

describe("openCollapsedGroups", () => {
    afterEach(() => {
        document.body.innerHTML = ""
    })

    it("clicks open every collapsed ancestor group between the target and the form root, via TaskObject's data-* hooks rather than its class names", () => {
        document.body.innerHTML = `
            <div class="renamed-group-wrapper" data-collapsible-group data-group-open="false">
                <button type="button" class="renamed-group-toggle" data-group-toggle></button>
                <div class="renamed-group-body">
                    <div id="target"><input /></div>
                </div>
            </div>
        `
        const group = document.querySelector("[data-collapsible-group]") as HTMLElement
        const groupToggle = document.querySelector("[data-group-toggle]") as HTMLElement
        groupToggle.addEventListener("click", () => group.setAttribute("data-group-open", "true"))
        const target = document.getElementById("target") as HTMLElement

        expect(group.getAttribute("data-group-open")).toBe("false")

        openCollapsedGroups(target)

        expect(group.getAttribute("data-group-open")).toBe("true")
    })

    it("does nothing when no ancestor group is collapsed", () => {
        document.body.innerHTML = `
            <div data-collapsible-group data-group-open="true">
                <button type="button" data-group-toggle></button>
                <div id="target"></div>
            </div>
        `
        const groupToggle = document.querySelector("[data-group-toggle]") as HTMLElement
        const clicked = vi.fn()
        groupToggle.addEventListener("click", clicked)

        openCollapsedGroups(document.getElementById("target") as HTMLElement)

        expect(clicked).not.toHaveBeenCalled()
    })
})
