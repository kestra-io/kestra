import {describe, expect, it} from "vitest"
import {isPlainRowActivation} from "./rowNavigation"

const clickOn = (html: string, init: MouseEventInit = {}) => {
    const host = document.createElement("div")
    host.innerHTML = html
    const target = host.firstElementChild!
    const event = new MouseEvent("click", {bubbles: true, ...init})
    Object.defineProperty(event, "target", {value: target})
    return event
}

describe("isPlainRowActivation", () => {
    it("accepts a plain click on a cell", () => {
        expect(isPlainRowActivation(undefined, clickOn("<span>cell</span>"))).toBe(true)
    })

    it("ignores the selection column", () => {
        expect(isPlainRowActivation({type: "selection"}, clickOn("<span />"))).toBe(false)
    })

    it.each([{metaKey: true}, {ctrlKey: true}, {shiftKey: true}, {altKey: true}, {button: 1}])("ignores a modified click %o", (init) => {
        expect(isPlainRowActivation(undefined, clickOn("<span />", init))).toBe(false)
    })

    it.each(["<a href='#'>flow</a>", "<button>x</button>", "<span role='button'>x</span>"])("leaves %s to the element itself", (html) => {
        expect(isPlainRowActivation(undefined, clickOn(html))).toBe(false)
    })
})
