import {describe, expect, it} from "vitest"
import {useTimeRangeSelection} from "./useTimeRangeSelection"

// Maps clientX 0..100px onto timestamps 1000..2000ms, 1:1 for simple assertions.
const timeAt = (clientX: number) => 1000 + clientX * 10

describe("useTimeRangeSelection", () => {
    it("should not create a selection when the pointer barely moves", () => {
        const {onPointerDown, onPointerUp, selection} = useTimeRangeSelection(timeAt)

        onPointerDown({button: 0, clientX: 10})
        const wasDrag = onPointerUp({clientX: 11})

        expect(wasDrag).toBe(false)
        expect(selection.value).toBeUndefined()
    })

    it("should create a selection spanning the dragged range", () => {
        const {onPointerDown, onPointerMove, onPointerUp, selection} = useTimeRangeSelection(timeAt)

        onPointerDown({button: 0, clientX: 10})
        onPointerMove({clientX: 40})
        const wasDrag = onPointerUp({clientX: 40})

        expect(wasDrag).toBe(true)
        expect(selection.value).toEqual({start: 1100, end: 1400})
    })

    it("should normalize a right-to-left drag into an ascending range", () => {
        const {onPointerDown, onPointerUp, selection} = useTimeRangeSelection(timeAt)

        onPointerDown({button: 0, clientX: 40})
        onPointerUp({clientX: 10})

        expect(selection.value).toEqual({start: 1100, end: 1400})
    })

    it("should ignore non-primary button presses", () => {
        const {onPointerDown, isDragging} = useTimeRangeSelection(timeAt)

        onPointerDown({button: 2, clientX: 10})

        expect(isDragging.value).toBe(false)
    })

    it("should clear an existing selection", () => {
        const {onPointerDown, onPointerUp, clear, selection, hasSelection} = useTimeRangeSelection(timeAt)

        onPointerDown({button: 0, clientX: 10})
        onPointerUp({clientX: 40})
        expect(hasSelection.value).toBe(true)

        clear()

        expect(selection.value).toBeUndefined()
        expect(hasSelection.value).toBe(false)
    })
})
