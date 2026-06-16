export interface FlatPlacement {
    targetLeafId: string;
    targetIndex: number;
}

export function computePlacement(
    orderedIds: string[],
    draggedId: string,
    groupIdOf: (id: string) => string | undefined,
): FlatPlacement | null {
    const idx = orderedIds.indexOf(draggedId)
    if (idx === -1) return null

    const referenceId = idx > 0 ? orderedIds[idx - 1] : orderedIds[idx + 1]
    const targetLeafId = referenceId ? groupIdOf(referenceId) : undefined
    if (!targetLeafId) return null

    let targetIndex = 0
    for (let i = 0; i < idx; i++) {
        if (groupIdOf(orderedIds[i]) === targetLeafId) targetIndex++
    }

    return {targetLeafId, targetIndex}
}
