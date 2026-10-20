import {describe, expect, it, vi} from "vitest"
import {useDragAndDrop} from "./useDragAndDrop"

describe("useDragAndDrop", () => {
    const createDragEvent = (withDataTransfer = true) => {
        const dataTransfer = withDataTransfer
            ? {
                  effectAllowed: "",
                  dropEffect: "",
                  setData: vi.fn(),
              }
            : undefined

        return {
            preventDefault: vi.fn(),
            dataTransfer,
        } as unknown as DragEvent
    }

    it("records the dragged index and sets effectAllowed to move", () => {
        const {draggedIndex, handleDragStart} = useDragAndDrop()
        const event = createDragEvent()

        handleDragStart(event, 2)

        expect(draggedIndex.value).toBe(2)
        expect(event.dataTransfer?.effectAllowed).toBe("move")
    })

    it("sets the dragged index on dataTransfer as text/plain", () => {
        const {handleDragStart} = useDragAndDrop()
        const event = createDragEvent()

        handleDragStart(event, 3)

        expect(event.dataTransfer?.setData).toHaveBeenCalledWith("text/plain", "3")
    })

    it("prevents default, records the hovered index and sets dropEffect to move", () => {
        const {dragOverIndex, handleDragOver} = useDragAndDrop()
        const event = createDragEvent()

        handleDragOver(event, 4)

        expect(event.preventDefault).toHaveBeenCalledOnce()
        expect(dragOverIndex.value).toBe(4)
        expect(event.dataTransfer?.dropEffect).toBe("move")
    })

    it("calls onReorder with the source and target indices when dropped", () => {
        const {handleDragStart, handleDrop} = useDragAndDrop()
        const onReorder = vi.fn()
        const startEvent = createDragEvent()
        const dropEvent = createDragEvent()

        handleDragStart(startEvent, 1)
        handleDrop(dropEvent, 4, onReorder)

        expect(dropEvent.preventDefault).toHaveBeenCalledOnce()
        expect(onReorder).toHaveBeenCalledWith(1, 4)
    })

    it("does not reorder when the target equals the dragged index", () => {
        const {handleDragStart, handleDrop} = useDragAndDrop()
        const onReorder = vi.fn()

        handleDragStart(createDragEvent(), 2)
        handleDrop(createDragEvent(), 2, onReorder)

        expect(onReorder).not.toHaveBeenCalled()
    })

    it("does not reorder when no drag has started", () => {
        const {handleDrop} = useDragAndDrop()
        const onReorder = vi.fn()

        handleDrop(createDragEvent(), 3, onReorder)

        expect(onReorder).not.toHaveBeenCalled()
    })

    it("clears both indices after a drop", () => {
        const {draggedIndex, dragOverIndex, handleDragStart, handleDragOver, handleDrop} =
            useDragAndDrop()

        handleDragStart(createDragEvent(), 1)
        handleDragOver(createDragEvent(), 3)
        handleDrop(createDragEvent(), 3, vi.fn())

        expect(draggedIndex.value).toBeNull()
        expect(dragOverIndex.value).toBeNull()
    })

    it("clears both indices after handleDragEnd", () => {
        const {draggedIndex, dragOverIndex, handleDragStart, handleDragOver, handleDragEnd} =
            useDragAndDrop()

        handleDragStart(createDragEvent(), 1)
        handleDragOver(createDragEvent(), 3)
        handleDragEnd()

        expect(draggedIndex.value).toBeNull()
        expect(dragOverIndex.value).toBeNull()
    })

    it("does not throw when dataTransfer has no setData", () => {
        const {draggedIndex, handleDragStart} = useDragAndDrop()
        const event = {
            preventDefault: vi.fn(),
            dataTransfer: {effectAllowed: "", dropEffect: ""},
        } as unknown as DragEvent

        expect(() => handleDragStart(event, 2)).not.toThrow()
        expect(draggedIndex.value).toBe(2)
        expect(event.dataTransfer?.effectAllowed).toBe("move")
    })

    it("does not throw when dataTransfer is absent", () => {
        const {handleDragStart, handleDragOver} = useDragAndDrop()
        const event = createDragEvent(false)

        expect(() => handleDragStart(event, 2)).not.toThrow()
        expect(() => handleDragOver(event, 3)).not.toThrow()
    })
})
