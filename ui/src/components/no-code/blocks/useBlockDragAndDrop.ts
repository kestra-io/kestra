import {type Ref} from "vue"
import {reorderAtPath, type BlockSection} from "../../../utils/flowableBlockOps"
import {useDragAndDrop} from "../../../composables/useDragAndDrop"
import {ALL_SECTIONS} from "./blockSections"

export interface SectionDnd {
    dragOverIndex: Ref<number | null>
    handleDragStart: (event: DragEvent, index: number) => void
    handleDragOver: (event: DragEvent, index: number) => void
    handleDragEnd: () => void
    handleDrop: (event: DragEvent, targetIndex: number) => void
}

export function useBlockDragAndDrop(
    flowYaml: Ref<string>,
    applyYaml: (yaml: string) => void,
    clearSelectionIfPathStale: (parentPath: string, from: number, to: number) => void,
) {
    function reorder(parentPath: string, from: number, to: number) {
        clearSelectionIfPathStale(parentPath, from, to)
        applyYaml(reorderAtPath(flowYaml.value, parentPath, from, to))
    }

    function bundleFor(section: BlockSection): SectionDnd {
        const dnd = useDragAndDrop()
        return {
            dragOverIndex: dnd.dragOverIndex,
            handleDragStart: dnd.handleDragStart,
            handleDragOver: dnd.handleDragOver,
            handleDragEnd: dnd.handleDragEnd,
            handleDrop: (event, targetIndex) => dnd.handleDrop(event, targetIndex, (from, to) => reorder(section, from, to)),
        }
    }

    const bundles = new Map<BlockSection, SectionDnd>(
        ALL_SECTIONS.map(section => [section, bundleFor(section)]),
    )

    function dndFor(section: BlockSection): SectionDnd {
        return bundles.get(section) as SectionDnd
    }

    return {dndFor, reorder}
}
