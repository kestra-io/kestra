import {describe, test, expect, vi} from "vitest"
import {useDragAndDrop} from "./useDragAndDrop"

const makeEvent = (clientY: number, rectTop: number, rectHeight: number): DragEvent => ({
    clientY,
    currentTarget: {
        getBoundingClientRect: () => ({top: rectTop, height: rectHeight} as DOMRect),
    },
} as DragEvent)

describe("useDragAndDrop", () => {
    test("start records the dragged id and seeds both the original and preview orders", () => {
        const {draggedId, previewIds, start} = useDragAndDrop(vi.fn())

        start("b", ["a", "b", "c"])

        expect(draggedId.value).toBe("b")
        expect(previewIds.value).toEqual(["a", "b", "c"])
    })

    test("over is a no-op when nothing is being dragged, or when the hovered id is the dragged one", () => {
        const event = makeEvent(105, 100, 20)

        const idle = useDragAndDrop(vi.fn())
        idle.over("a", event)
        expect(idle.previewIds.value).toBeNull()

        const {previewIds, start, over} = useDragAndDrop(vi.fn())
        start("b", ["a", "b", "c"])
        const seeded = previewIds.value

        over("b", event)

        expect(previewIds.value).toBe(seeded)
    })

    test("dropping above the vertical midpoint of the target inserts before it", () => {
        const {previewIds, start, over} = useDragAndDrop(vi.fn())
        start("c", ["a", "b", "c"])

        // rect top=100, height=20 -> midpoint=110; clientY=105 is above it.
        over("a", makeEvent(105, 100, 20))

        expect(previewIds.value).toEqual(["c", "a", "b"])
    })

    test("dropping below the vertical midpoint of the target inserts after it", () => {
        const {previewIds, start, over} = useDragAndDrop(vi.fn())
        start("c", ["a", "b", "c"])

        // rect top=100, height=20 -> midpoint=110; clientY=115 is below it.
        over("a", makeEvent(115, 100, 20))

        expect(previewIds.value).toEqual(["a", "c", "b"])
    })

    test("over returns without changing previewIds when the computed order matches the current preview", () => {
        const {previewIds, start, over} = useDragAndDrop(vi.fn())
        start("b", ["a", "b", "c"])
        const event = makeEvent(105, 100, 20)

        over("a", event)
        const firstOrder = previewIds.value

        over("a", event)

        // Same array reference: the second call took the early-return branch
        // rather than recomputing and reassigning an equal array.
        expect(previewIds.value).toBe(firstOrder)
    })

    test("over is a no-op when the dragged id or the hovered id is not in the list", () => {
        const event = makeEvent(105, 100, 20)

        const draggedMissing = useDragAndDrop(vi.fn())
        draggedMissing.start("z", ["a", "b", "c"])
        draggedMissing.over("a", event)
        expect(draggedMissing.previewIds.value).toEqual(["a", "b", "c"])

        const hoveredMissing = useDragAndDrop(vi.fn())
        hoveredMissing.start("b", ["a", "b", "c"])
        hoveredMissing.over("missing", event)
        expect(hoveredMissing.previewIds.value).toEqual(["a", "b", "c"])
    })

    test("drop calls commit with the preview order and the dragged id, then resets", () => {
        const commit = vi.fn()
        const {draggedId, previewIds, start, over, drop} = useDragAndDrop(commit)
        start("c", ["a", "b", "c"])
        over("a", makeEvent(105, 100, 20))

        drop()

        expect(commit).toHaveBeenCalledWith(["c", "a", "b"], "c")
        expect(draggedId.value).toBeNull()
        expect(previewIds.value).toBeNull()
    })

    test("drop without a preceding start does not call commit", () => {
        const commit = vi.fn()
        const {drop} = useDragAndDrop(commit)

        drop()

        expect(commit).not.toHaveBeenCalled()
    })

    test("reset clears draggedId and previewIds", () => {
        const {draggedId, previewIds, start, reset} = useDragAndDrop(vi.fn())
        start("b", ["a", "b", "c"])

        reset()

        expect(draggedId.value).toBeNull()
        expect(previewIds.value).toBeNull()
    })
})
