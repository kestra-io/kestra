import {computed, ref} from "vue"

export interface TimeRange {
    start: number
    end: number
}

// Below this pointer travel, a press-and-release is treated as a click rather than a drag —
// mirrors the tolerance Grafana-style graphs use to disambiguate the two gestures.
const DRAG_THRESHOLD_PX = 4

/**
 * Grafana-style drag-to-select over a horizontal time axis. The caller supplies how to turn a
 * `clientX` into a timestamp (`timeAt`); this composable only tracks the pointer gesture and the
 * resulting selection, so it stays agnostic of how the axis is laid out or rendered.
 */
export function useTimeRangeSelection(timeAt: (clientX: number) => number) {
    const dragStartX = ref<number | undefined>(undefined)
    const dragCurrentX = ref<number | undefined>(undefined)
    const selection = ref<TimeRange | undefined>(undefined)

    const isDragging = computed(() => dragStartX.value !== undefined)
    const hasSelection = computed(() => selection.value !== undefined)

    // Live pixel band while dragging, for the caller to render a preview before the gesture ends.
    const dragBandPx = computed(() => {
        if (dragStartX.value === undefined || dragCurrentX.value === undefined) return undefined
        return {
            from: Math.min(dragStartX.value, dragCurrentX.value),
            to: Math.max(dragStartX.value, dragCurrentX.value),
        }
    })

    function onPointerDown(event: {button: number; clientX: number}) {
        if (event.button !== 0) return
        dragStartX.value = event.clientX
        dragCurrentX.value = event.clientX
    }

    function onPointerMove(event: {clientX: number}) {
        if (dragStartX.value === undefined) return
        dragCurrentX.value = event.clientX
    }

    /** @return true when the gesture was a drag — callers should suppress a click handled on the same target. */
    function onPointerUp(event: {clientX: number}): boolean {
        if (dragStartX.value === undefined) return false

        const startX = dragStartX.value
        const distance = Math.abs(event.clientX - startX)
        dragStartX.value = undefined
        dragCurrentX.value = undefined

        if (distance < DRAG_THRESHOLD_PX) {
            return false
        }

        const startTime = timeAt(startX)
        const endTime = timeAt(event.clientX)
        selection.value = startTime <= endTime
            ? {start: startTime, end: endTime}
            : {start: endTime, end: startTime}

        return true
    }

    function clear() {
        selection.value = undefined
    }

    // Defensive reset for a gesture that never reaches onPointerUp (pointercancel, or the pointer
    // capture that setPointerCapture relies on being lost some other way) — without this, a drag
    // interrupted off-track leaves dragStartX/dragCurrentX set and the preview band stuck on screen.
    function cancelDrag() {
        dragStartX.value = undefined
        dragCurrentX.value = undefined
    }

    return {
        selection,
        hasSelection,
        isDragging,
        dragBandPx,
        onPointerDown,
        onPointerMove,
        onPointerUp,
        cancelDrag,
        clear,
    }
}
