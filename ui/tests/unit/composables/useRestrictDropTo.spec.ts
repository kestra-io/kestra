import {afterEach, beforeEach, describe, expect, test} from "vitest"
import {ref} from "vue"
import {useRestrictDropTo} from "../../../src/composables/useRestrictDropTo"

describe("useRestrictDropTo", () => {
    let container: HTMLElement
    let outside: HTMLElement
    let restrict: ReturnType<typeof useRestrictDropTo>

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

    test.each(["dragover", "dragenter"])("%s never reaches a target outside the container", (type) => {
        restrict.start()

        const event = dragEvent(type)
        outside.dispatchEvent(event)

        expect(reachedOutside).toBe(false)
        expect(event.defaultPrevented).toBe(true)
        expect(event.dataTransfer?.dropEffect).toBe("none")
    })

    test("the container keeps handling its own drags", () => {
        restrict.start()

        const inside = document.createElement("div")
        container.appendChild(inside)

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
})
