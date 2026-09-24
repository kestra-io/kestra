import {computed, inject} from "vue"
import {useDragAndDrop} from "../../../composables/useDragAndDrop"
import {BLOCK_DRAG_INJECTION_KEY} from "../injectionKeys"

/**
 * The per-item drag/drop wiring a lane (a top-level section or a nested branch) needs to accept a
 * cross-level move: sibling reorder within the lane keeps its own `dragOverIndex` insertion cue,
 * while every accept/refuse decision and the move itself are delegated to the shared
 * `BlockDragContext` so a card dragged out of one lane can land in another.
 */
export function useLaneDrag(parentPath: () => string, itemCount: () => number) {
    const dragContext = inject(BLOCK_DRAG_INJECTION_KEY)
    const {dragOverIndex, handleDragStart, handleDragOver, handleDragEnd} = useDragAndDrop()

    const dropState = computed<"idle" | "allowed" | "forbidden">(() => {
        if (!dragContext?.draggedPath.value) return "idle"
        return dragContext.canDropIn(parentPath()) ? "allowed" : "forbidden"
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
        onTrailingDragOver,
        onTrailingDrop,
    }
}
