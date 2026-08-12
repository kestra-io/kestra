import {describe, expect, it, vi} from "vitest"
import {nextTick, ref} from "vue"
import {useCanvasFocus} from "../../../../../src/components/no-code/blocks/useCanvasFocus"

// jsdom reports every element as having no offsetParent and has no layout, so the
// two things the composable relies on — visibility filtering and scrollIntoView —
// are stubbed per element.
function buildCanvas(ids: string[]) {
    const root = document.createElement("div")

    for (const id of ids) {
        const card = document.createElement("div")
        card.setAttribute("data-block-id", id)
        card.setAttribute("tabindex", "-1")
        Object.defineProperty(card, "offsetParent", {get: () => root})
        card.scrollIntoView = vi.fn()
        root.appendChild(card)
    }

    document.body.appendChild(root)
    return root
}

function scrollSpy(root: HTMLElement, id: string) {
    return root.querySelector<HTMLElement>(`[data-block-id='${id}']`)!.scrollIntoView as ReturnType<typeof vi.fn>
}

function setup(ids: string[]) {
    const root = buildCanvas(ids)
    const editorEl = ref<HTMLElement | undefined>(root)
    // useCanvasFocus registers no lifecycle hooks, so it runs outside a component.
    return {root, focus: useCanvasFocus(editorEl, () => [])}
}

describe("useCanvasFocus — scroll alignment", () => {
    it("scrolls the minimum distance by default", async () => {
        const {root, focus} = setup(["a", "b"])

        focus.focusCanvasCard("b")
        await nextTick()

        expect(scrollSpy(root, "b")).toHaveBeenCalledWith({block: "nearest"})
    })

    it("centres the card when asked to", async () => {
        const {root, focus} = setup(["a", "b"])

        focus.focusCanvasCard("b", {align: "center"})
        await nextTick()

        // A palette jump crosses the whole canvas; `nearest` would leave the
        // destination at the very edge of the viewport.
        expect(scrollSpy(root, "b")).toHaveBeenCalledWith({block: "center"})
    })

    it("keeps arrow-key stepping on the minimum distance", async () => {
        const {root, focus} = setup(["a", "b", "c"])

        focus.focusCanvasCard("a")
        await nextTick()
        focus.moveFocus(1)
        await nextTick()

        expect(focus.focusedId.value).toBe("b")
        expect(scrollSpy(root, "b")).toHaveBeenCalledWith({block: "nearest"})
    })

    it("tracks the focused id and does nothing without one", async () => {
        const {root, focus} = setup(["a"])

        focus.focusCanvasCard(undefined)
        await nextTick()

        expect(focus.focusedId.value).toBeUndefined()
        expect(scrollSpy(root, "a")).not.toHaveBeenCalled()
    })
})
