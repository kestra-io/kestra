import {ref} from "vue"

export function useDragAndDrop(commit: (orderedIds: string[], draggedId: string) => void) {
    const draggedId = ref<string | null>(null)
    const previewIds = ref<string[] | null>(null)
    const originalIds = ref<string[]>([])

    const start = (id: string, currentIds: string[]) => {
        draggedId.value = id
        originalIds.value = [...currentIds]
        previewIds.value = [...currentIds]
    }

    const over = (overId: string, event: DragEvent) => {
        if (!draggedId.value || overId === draggedId.value) return
        const order = [...originalIds.value]
        const from = order.indexOf(draggedId.value)
        if (from === -1) return
        order.splice(from, 1)
        let insertAt = order.indexOf(overId)
        if (insertAt === -1) return
        const target = event.currentTarget as HTMLElement | null
        if (target) {
            const rect = target.getBoundingClientRect()
            if (event.clientY > rect.top + rect.height / 2) insertAt += 1
        }
        order.splice(insertAt, 0, draggedId.value)
        if (previewIds.value?.length === order.length && previewIds.value.every((id, i) => id === order[i])) return
        previewIds.value = order
    }

    const drop = () => {
        if (draggedId.value && previewIds.value) commit(previewIds.value, draggedId.value)
        reset()
    }

    const reset = () => {
        draggedId.value = null
        previewIds.value = null
        originalIds.value = []
    }

    return {draggedId, previewIds, start, over, drop, reset}
}
