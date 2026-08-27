import {afterEach, beforeEach, describe, expect, test} from "vitest"
import {ref} from "vue"
import {useRestrictDropTo} from "../../../src/composables/useRestrictDropTo"

// A drag that may only land in one widget still travels over the rest of the app, where other drop
// targets cancel dragover to accept drops of their own — which is what shows a move cursor and
// advertises a drop that never happens (#9497).
describe("useRestrictDropTo", () => {
    let container: HTMLElement
    let inside: HTMLElement
    let outside: HTMLElement
    let restrict: ReturnType<typeof useRestrictDropTo>

    // Stands in for an unrelated drop target: it accepts whatever it is offered
    let reachedOutside: boolean
    const acceptDrop = (event: Event) => {
        reachedOutside = true
        event.preventDefault()
    }

    function dragEvent(type: string) {
        const event = new Event(type, {bubbles: true, cancelable: true}) as DragEvent
        Object.defineProperty(event, "dataTransfer", {value: {dropEffect: "move"}})
        return event
    }

    beforeEach(() => {
        container = document.createElement("div")
        inside = document.createElement("div")
        container.appendChild(inside)
        outside = document.createElement("div")
        document.body.append(container, outside)

        reachedOutside = false
        outside.addEventListener("dragover", acceptDrop)

        restrict = useRestrictDropTo(ref(container))
    })

    afterEach(() => {
        restrict.stop()
        container.remove()
        outside.remove()
    })

    test("an unrelated target accepts drags while none is restricted", () => {
        const event = dragEvent("dragover")
        outside.dispatchEvent(event)

        expect(reachedOutside).toBe(true)
        expect(event.defaultPrevented).toBe(true)
    })

    test.each(["dragover", "dragenter"])("%s never reaches a target outside the container", (type) => {
        restrict.start()

        const event = dragEvent(type)
        outside.dispatchEvent(event)

        expect(reachedOutside).toBe(false)
        // Cancelled with no operation: the explicit refusal, which an editable element's default
        // acceptance of dropped text would otherwise override
        expect(event.defaultPrevented).toBe(true)
        expect(event.dataTransfer?.dropEffect).toBe("none")
    })

    test("refuses over an editable element, whose default is to accept dropped text", () => {
        const editable = document.createElement("textarea")
        document.body.appendChild(editable)
        restrict.start()

        const event = dragEvent("dragover")
        editable.dispatchEvent(event)

        expect(event.defaultPrevented).toBe(true)
        expect(event.dataTransfer?.dropEffect).toBe("none")

        editable.remove()
    })

    test("the container keeps handling its own drags", () => {
        restrict.start()

        let reachedContainer = false
        container.addEventListener("dragover", () => {
            reachedContainer = true
        })
        inside.dispatchEvent(dragEvent("dragover"))

        expect(reachedContainer).toBe(true)
    })

    test("ending the drag releases the rest of the app", () => {
        restrict.start()
        document.dispatchEvent(dragEvent("dragend"))

        outside.dispatchEvent(dragEvent("dragover"))

        expect(reachedOutside).toBe(true)
    })

    test("a second drag does not stack a duplicate set of listeners", () => {
        restrict.start()
        restrict.start()

        // One dragend has to be enough to undo both starts
        document.dispatchEvent(dragEvent("dragend"))
        outside.dispatchEvent(dragEvent("dragover"))

        expect(reachedOutside).toBe(true)
    })
})
