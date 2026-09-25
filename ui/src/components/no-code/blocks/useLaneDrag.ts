import {computed, inject, watch} from "vue"
import {useDragAndDrop} from "../../../composables/useDragAndDrop"
import {BLOCK_DRAG_INJECTION_KEY} from "../injectionKeys"

/** The drag/drop wiring a lane needs to accept a cross-level move: `dragOverIndex` stays local for the sibling-reorder insertion cue, while every accept/refuse decision and the move itself go through the shared `BlockDragContext` so a card dragged out of this lane can land in another. */
export function useLaneDrag(parentPath: () => string, itemCount: () => number) {
    const dragContext = inject(BLOCK_DRAG_INJECTION_KEY, undefined)
    const {dragOverIndex, handleDragStart, handleDragOver, handleDragEnd} = useDragAndDrop()

    const dropState = computed<"idle" | "allowed" | "forbidden">(() => {
        if (!dragContext?.draggedPath.value) return "idle"
        return dragContext.canDropIn(parentPath()) ? "allowed" : "forbidden"
    })

    watch(() => dragContext?.draggedPath.value, (path) => {
        if (!path) dragOverIndex.value = null
    })

    function onItemDragStart(event: DragEvent, index: number) {
        handleDragStart(event, index)
        dragContext?.beginDrag(`${parentPath()}[${index}]`)
    }

    function onItemDragOver(event: DragEvent, index: number) {
        if (!dragContext?.canDropIn(parentPath())) return
        handleDragOver(event, index)
    }

    function onItemDrop(event: DragEvent, index: number) {
        if (!dragContext?.canDropIn(parentPath())) return
        event.preventDefault()
        event.stopPropagation()
        dragContext.dropAt(parentPath(), index)
        handleDragEnd()
    }

    function onDragEnd() {
        handleDragEnd()
        dragContext?.endDrag()
    }

    /** Clears the insertion cue when the drag leaves this lane's container entirely, so it does not linger while the pointer hovers another lane. `relatedTarget` is null for a drag that leaves the window, which counts as leaving the lane too. */
    function onLaneDragLeave(event: DragEvent) {
        const container = event.currentTarget as Node | null
        const related = event.relatedTarget as Node | null
        if (container && related && container.contains(related)) return
        dragOverIndex.value = null
    }

    function onTrailingDragOver(event: DragEvent) {
        if (!dragContext?.canDropIn(parentPath())) return
        event.preventDefault()
    }

    function onTrailingDrop(event: DragEvent) {
        if (!dragContext?.canDropIn(parentPath())) return
        event.preventDefault()
        event.stopPropagation()
        dragContext.dropAt(parentPath(), itemCount())
        onDragEnd()
    }

    return {
        dragOverIndex,
        dropState,
        onItemDragStart,
        onItemDragOver,
        onItemDrop,
        onDragEnd,
        onLaneDragLeave,
        onTrailingDragOver,
        onTrailingDrop,
    }
}
