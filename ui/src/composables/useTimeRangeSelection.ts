import {computed, ref} from "vue"

export interface TimeRange {
    start: number
    end: number
}

const DRAG_THRESHOLD_PX = 4

export function useTimeRangeSelection(timeAt: (clientX: number) => number) {
    const dragStartX = ref<number | undefined>(undefined)
    const dragCurrentX = ref<number | undefined>(undefined)
    const selection = ref<TimeRange | undefined>(undefined)

    const isDragging = computed(() => dragStartX.value !== undefined)
    const hasSelection = computed(() => selection.value !== undefined)

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
