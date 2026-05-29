import {ref, type Ref} from "vue"

export function useDragAndDrop(): {
    draggedIndex: Ref<number | null>;
    dragOverIndex: Ref<number | null>;
    handleDragStart: (event: DragEvent, index: number) => void;
    handleDragOver: (event: DragEvent, index: number) => void;
    handleDrop: (event: DragEvent, targetIndex: number, onReorder: (fromIndex: number, toIndex: number) => void) => void;
    handleDragEnd: () => void;
} {
    const draggedIndex = ref<number | null>(null)
    const dragOverIndex = ref<number | null>(null)

    const handleDragStart = (event: DragEvent, index: number): void => {
        draggedIndex.value = index
        if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = "move"
        }
    }

    const handleDragOver = (event: DragEvent, index: number): void => {
        event.preventDefault()
        dragOverIndex.value = index
        if (event.dataTransfer) {
            event.dataTransfer.dropEffect = "move"
        }
    }

    const handleDrop = (event: DragEvent, targetIndex: number, onReorder: (fromIndex: number, toIndex: number) => void): void => {
        event.preventDefault()
        if (draggedIndex.value != null && draggedIndex.value !== targetIndex) {
            onReorder(draggedIndex.value, targetIndex)
        }
        handleDragEnd()
    }

    const handleDragEnd = (): void => {
        draggedIndex.value = dragOverIndex.value = null
    }

    return {
        draggedIndex: draggedIndex,
        dragOverIndex: dragOverIndex,
        handleDragStart: handleDragStart,
        handleDragOver: handleDragOver,
        handleDrop: handleDrop,
        handleDragEnd: handleDragEnd,
    }
}