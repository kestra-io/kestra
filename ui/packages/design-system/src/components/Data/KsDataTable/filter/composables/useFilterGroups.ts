import {computed, ref} from "vue"
import {
    type AppliedFilter,
    type FilterGroup,
    type LeafFilterGroup,
    type LogicalOperator,
    type WrapperGroup,
    isWrapperGroup,
} from "../utils/filterTypes"

export const newGroupId = (): string =>
    `g${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

export const emptyLeafGroup = (): LeafFilterGroup => ({
    id: newGroupId(),
    kind: "leaf",
    filters: [],
})

export const allFilters = (units: FilterGroup[]): AppliedFilter[] =>
    units.flatMap(u => isWrapperGroup(u) ? u.children.flatMap(c => c.filters) : u.filters)

export const findLeafContaining = (
    units: FilterGroup[],
    filterId: string,
): LeafFilterGroup | undefined => {
    for (const unit of units) {
        if (isWrapperGroup(unit)) {
            const child = unit.children.find(c => c.filters.some(f => f?.id === filterId))
            if (child) return child
        } else if (unit.filters.some(f => f?.id === filterId)) {
            return unit
        }
    }
    return undefined
}

export const findLeafById = (units: FilterGroup[], leafId: string): LeafFilterGroup | undefined => {
    for (const unit of units) {
        if (isWrapperGroup(unit)) {
            const child = unit.children.find(c => c.id === leafId)
            if (child) return child
        } else if (unit.id === leafId) {
            return unit
        }
    }
    return undefined
}

export function useFilterGroups() {
    const groups = ref<FilterGroup[]>([emptyLeafGroup()])
    const topLogical = ref<LogicalOperator>("OR")

    const appliedFilters = computed<AppliedFilter[]>(() => allFilters(groups.value))

    const updateLeaf = (
        leafId: string,
        mutate: (leaf: LeafFilterGroup) => LeafFilterGroup,
    ): boolean => {
        let found = false
        groups.value = groups.value.map(unit => {
            if (isWrapperGroup(unit)) {
                const newChildren = unit.children.map(c => {
                    if (c.id !== leafId) return c
                    found = true
                    return mutate(c)
                })
                return found ? {...unit, children: newChildren} : unit
            }
            if (unit.id !== leafId) return unit
            found = true
            return mutate(unit as LeafFilterGroup)
        })
        return found
    }

    const addGroup = () => {
        groups.value = [...groups.value, emptyLeafGroup()]
    }

    const removeGroup = (groupId: string) => {
        if (groups.value.length <= 1) return
        const next = groups.value.filter(g => g.id !== groupId)
        groups.value = next.length > 0 ? next : [emptyLeafGroup()]
    }

    const wrapGroups = (sourceGroupId: string, targetGroupId: string) => {
        if (sourceGroupId === targetGroupId) return
        const sourceIdx = groups.value.findIndex(g => g.id === sourceGroupId)
        const targetIdx = groups.value.findIndex(g => g.id === targetGroupId)
        if (sourceIdx < 0 || targetIdx < 0) return
        const source = groups.value[sourceIdx]
        const target = groups.value[targetIdx]
        if (isWrapperGroup(source)) return

        const sourceLeaf = source as LeafFilterGroup
        const wrapped: WrapperGroup = isWrapperGroup(target)
            ? {...target, children: [...target.children, sourceLeaf]}
            : {
                id: newGroupId(),
                kind: "wrapper",
                logical: "AND",
                children: [target as LeafFilterGroup, sourceLeaf],
            }

        groups.value = groups.value
            .map((g, i) => (i === targetIdx ? wrapped : g))
            .filter((_, i) => i !== sourceIdx)
    }

    const unwrapGroup = (wrapperId: string) => {
        const idx = groups.value.findIndex(g => g.id === wrapperId)
        if (idx < 0) return
        const unit = groups.value[idx]
        if (!isWrapperGroup(unit)) return
        const before = groups.value.slice(0, idx)
        const after = groups.value.slice(idx + 1)
        groups.value = [...before, ...unit.children, ...after]
    }

    const moveFilter = (filterId: string, targetGroupId: string) => {
        const sourceLeaf = findLeafContaining(groups.value, filterId)
        const targetLeaf = findLeafById(groups.value, targetGroupId)
        if (!sourceLeaf || !targetLeaf || sourceLeaf.id === targetLeaf.id) return

        const filterToMove = sourceLeaf.filters.find(f => f?.id === filterId)
        if (!filterToMove) return

        updateLeaf(sourceLeaf.id, leaf => ({
            ...leaf,
            filters: leaf.filters.filter(f => f?.id !== filterId),
        }))
        updateLeaf(targetLeaf.id, leaf => ({
            ...leaf,
            filters: [
                ...leaf.filters.filter(f =>
                    !(f?.key === filterToMove.key && f?.comparator === filterToMove.comparator),
                ),
                filterToMove,
            ],
        }))
    }

    const setTopLogical = (op: LogicalOperator) => {
        if (topLogical.value === op) return
        topLogical.value = op
    }

    const setWrapperLogical = (wrapperId: string, op: LogicalOperator) => {
        groups.value = groups.value.map(unit => {
            if (!isWrapperGroup(unit) || unit.id !== wrapperId) return unit
            if (unit.logical === op) return unit
            return {...unit, logical: op}
        })
    }

    const replaceTree = (next: FilterGroup[], top: LogicalOperator = "OR") => {
        groups.value = next.length > 0 ? next : [emptyLeafGroup()]
        topLogical.value = top
    }

    const clearTree = () => {
        groups.value = [emptyLeafGroup()]
        topLogical.value = "OR"
    }

    return {
        groups: computed(() => groups.value),
        topLogical: computed(() => topLogical.value),
        appliedFilters,
        addGroup,
        removeGroup,
        wrapGroups,
        unwrapGroup,
        moveFilter,
        setTopLogical,
        setWrapperLogical,
        updateLeaf,
        replaceTree,
        clearTree,
    }
}

// export type UseFilterGroups = ReturnType<typeof useFilterGroups>
