/**
 * User-facing filter actions: chip CRUD (add/remove/update) plus structural operations
 * (move, wrap, unwrap, flip operators, add/remove group, clear all).
 *
 * Each action mutates the tree via `tree.*`, coordinates with the dismissed-key set, then
 * pushes the result to the URL via the injected `updateRoute` callback. Takes shared state
 * as parameters so it doesn't bundle URL-writing concerns.
 */
import type {Ref} from "vue"
import {
    type AppliedFilter,
    type LogicalOperator,
    isWrapperGroup,
} from "../utils/filterTypes"
import {
    findLeafById,
    findLeafContaining,
    type useFilterGroups,
} from "./useFilterGroups"
import type {useDismissedKeys} from "./useDismissedKeys"

type Tree = ReturnType<typeof useFilterGroups>
type Dismissed = ReturnType<typeof useDismissedKeys>

interface UseFilterActionsOptions {
    tree: Tree;
    dismissed: Dismissed;
    searchQuery: Ref<string>;
    /** Called after each action to push the new tree state to the URL. */
    updateRoute: (shouldResetPage?: boolean) => void;
    /** True if a filter has a non-empty value — controls whether we reset pagination. */
    hasValue: (filter: AppliedFilter) => boolean;
}

export function useFilterActions({
    tree,
    dismissed,
    searchQuery,
    updateRoute,
    hasValue,
}: UseFilterActionsOptions) {
    /** Resolve the leaf id that should receive a new filter (falls back to the last leaf). */
    const resolveTargetLeafId = (groupId?: string): string | undefined => {
        if (groupId) {
            const direct = findLeafById(tree.groups.value, groupId)
            if (direct) return direct.id
            const maybeWrapper = tree.groups.value.find(g => g.id === groupId)
            if (maybeWrapper && isWrapperGroup(maybeWrapper)) {
                return maybeWrapper.children.slice(-1)[0]?.id
            }
        }
        const lastTop = tree.groups.value[tree.groups.value.length - 1]
        if (!lastTop) return undefined
        return isWrapperGroup(lastTop)
            ? lastTop.children[lastTop.children.length - 1]?.id
            : lastTop.id
    }

    const addFilter = (filter: AppliedFilter, groupId?: string) => {
        dismissed.restoreDefaultVisibleKey(filter.key)
        const targetLeafId = resolveTargetLeafId(groupId)
        if (!targetLeafId) return

        tree.updateLeaf(targetLeafId, leaf => {
            // Replace only when key AND comparator match — same-field/different-op chips coexist.
            const idx = leaf.filters.findIndex(
                f => f?.key === filter?.key && f?.comparator === filter?.comparator,
            )
            return {
                ...leaf,
                filters: idx === -1
                    ? [...leaf.filters, filter]
                    : leaf.filters.map((f, j) => (j === idx ? filter : f)),
            }
        })
        updateRoute(hasValue(filter))
    }

    const removeFilter = (filterId: string) => {
        const enclosing = findLeafContaining(tree.groups.value, filterId)
        if (!enclosing) return
        const found = enclosing.filters.find(f => f?.id === filterId)
        if (!found) return
        tree.updateLeaf(enclosing.id, leaf => ({
            ...leaf,
            filters: leaf.filters.filter(f => f?.id !== filterId),
        }))
        dismissed.dismissDefaultVisibleKey(found.key)
        updateRoute(false)
    }

    const updateFilter = (updatedFilter: AppliedFilter) => {
        dismissed.restoreDefaultVisibleKey(updatedFilter.key)
        const enclosing = findLeafContaining(tree.groups.value, updatedFilter.id)
        if (!enclosing) return
        tree.updateLeaf(enclosing.id, leaf => ({
            ...leaf,
            filters: leaf.filters.map(f => (f?.id === updatedFilter.id ? updatedFilter : f)),
        }))
        updateRoute(hasValue(updatedFilter))
    }

    // Structural operations — delegate to the tree composable, then push to route.
    const moveFilter = (filterId: string, targetGroupId: string) => {
        tree.moveFilter(filterId, targetGroupId)
        updateRoute(false)
    }
    const wrapGroups = (sourceGroupId: string, targetGroupId: string) => {
        tree.wrapGroups(sourceGroupId, targetGroupId)
        updateRoute(false)
    }
    const unwrapGroup = (wrapperId: string) => {
        tree.unwrapGroup(wrapperId)
        updateRoute(false)
    }
    const setTopLogical = (op: LogicalOperator) => {
        tree.setTopLogical(op)
        updateRoute(false)
    }
    const setWrapperLogical = (wrapperId: string, op: LogicalOperator) => {
        tree.setWrapperLogical(wrapperId, op)
        updateRoute(false)
    }
    /** Empty groups don't affect the URL — the route updates when the user adds a filter. */
    const addGroup = () => tree.addGroup()
    const removeGroup = (groupId: string) => {
        if (tree.groups.value.length <= 1) return
        tree.removeGroup(groupId)
        updateRoute(false)
    }

    const clearFilters = () => {
        dismissed.dismissAllDefaultVisibleKeys()
        tree.clearTree()
        searchQuery.value = ""
        updateRoute(true)
    }

    return {
        addFilter,
        removeFilter,
        updateFilter,
        moveFilter,
        wrapGroups,
        unwrapGroup,
        setTopLogical,
        setWrapperLogical,
        addGroup,
        removeGroup,
        clearFilters,
    }
}
