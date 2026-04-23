import {ref} from "vue";

export function useDragAndDrop() {
    const draggedIndex = ref<number | null>(null);
    const dragOverIndex = ref<number | null>(null);

    const handleDragStart = (event: DragEvent, index: number) => {
        draggedIndex.value = index;
        if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = "move";
        }
    };

    const handleDragOver = (event: DragEvent, index: number) => {
        event.preventDefault();
        dragOverIndex.value = index;
        if (event.dataTransfer) {
            event.dataTransfer.dropEffect = "move";
        }
    };

    const handleDrop = (event: DragEvent, targetIndex: number, onReorder: (fromIndex: number, toIndex: number) => void) => {
        event.preventDefault();
        if (draggedIndex.value != null && draggedIndex.value !== targetIndex) {
            onReorder(draggedIndex.value, targetIndex);
        }
        handleDragEnd();
    };

    const handleDragEnd = () => {
        draggedIndex.value = dragOverIndex.value = null;
    };

    return {
        draggedIndex,
        dragOverIndex,
        handleDragStart,
        handleDragOver,
        handleDrop,
        handleDragEnd,
    };
}