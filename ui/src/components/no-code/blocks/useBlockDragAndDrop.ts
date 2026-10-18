import {ref, type Ref} from "vue"
import {canMoveBlockToPath, listLengthAtPath, moveBlockToPath, reorderAtPath} from "../../../utils/flowableBlockOps"

/** Lives above any single lane component, since a block dragged out of a `BranchLane` nested three levels down needs a sibling lane it knows nothing about to see it too. */
export interface BlockDragContext {
    draggedPath: Ref<string | null>
    beginDrag: (path: string) => void
    endDrag: () => void
    canDropIn: (parentPath: string) => boolean
    dropAt: (parentPath: string, index: number) => void
}

export function useBlockDragAndDrop(
    flowYaml: Ref<string>,
    applyYaml: (yaml: string) => void,
    clearSelectionIfPathStale: (parentPath: string, from: number, to: number) => void,
): BlockDragContext {
    const draggedPath = ref<string | null>(null)
    const allowedByParentPath = new Map<string, boolean>()

    function beginDrag(path: string) {
        draggedPath.value = path
        allowedByParentPath.clear()
    }

    function endDrag() {
        draggedPath.value = null
        allowedByParentPath.clear()
    }

    function canDropIn(parentPath: string): boolean {
        const source = draggedPath.value
        if (!source) return false
        const cached = allowedByParentPath.get(parentPath)
        if (cached !== undefined) return cached
        const allowed = canMoveBlockToPath(flowYaml.value, source, parentPath).allowed
        allowedByParentPath.set(parentPath, allowed)
        return allowed
    }

    function dropAt(toParentPath: string, toIndex: number) {
        const source = draggedPath.value
        if (!source || !canDropIn(toParentPath)) {
            endDrag()
            return
        }

        const fromMatch = source.match(/^(.*)\[(\d+)\]$/)
        if (!fromMatch) {
            endDrag()
            return
        }
        const [, fromParentPath, fromIndexRaw] = fromMatch
        const fromIndex = parseInt(fromIndexRaw, 10)

        if (fromParentPath === toParentPath) {
            // Same lane: keep the exact splice-based arithmetic reorderAtPath already ships with.
            const length = listLengthAtPath(flowYaml.value, toParentPath)
            const clampedIndex = Math.min(toIndex, length - 1)
            if (fromIndex !== clampedIndex) {
                clearSelectionIfPathStale(toParentPath, fromIndex, clampedIndex)
                applyYaml(reorderAtPath(flowYaml.value, toParentPath, fromIndex, clampedIndex))
            }
        } else {
            clearSelectionIfPathStale(fromParentPath, fromIndex, Math.max(fromIndex, listLengthAtPath(flowYaml.value, fromParentPath) - 1))
            clearSelectionIfPathStale(toParentPath, toIndex, listLengthAtPath(flowYaml.value, toParentPath))
            applyYaml(moveBlockToPath(flowYaml.value, source, toParentPath, toIndex))
        }

        endDrag()
    }

    return {draggedPath, beginDrag, endDrag, canDropIn, dropAt}
}
