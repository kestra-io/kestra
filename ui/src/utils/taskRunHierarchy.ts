/**
 * Orders a flat list of taskruns parent → child, annotating each with its nesting depth.
 *
 * A taskrun is nested under its parent only when that parent is also present in the list. Orphans
 * (a `parentTaskRunId` whose target is absent — e.g. a LOOP iteration execution whose tasks point at
 * the Loop run living in the parent execution) are treated as roots so they still render.
 *
 * @param taskRunList    the taskruns to order
 * @param compareSiblings optional comparator to sort siblings (e.g. by start date); when omitted the
 *                        original list order is preserved
 * @return the taskruns flattened depth-first, each paired with its depth (roots at depth 0)
 */
export function buildTaskRunHierarchy<T extends {id: string; parentTaskRunId?: string}>(
    taskRunList: T[],
    compareSiblings?: (a: T, b: T) => number,
): Array<{task: T; depth: number}> {
    const byId = new Set(taskRunList.map((tr) => tr.id))
    const childrenByParent: Record<string, T[]> = {}
    const roots: T[] = []

    for (const tr of taskRunList) {
        if (tr.parentTaskRunId && byId.has(tr.parentTaskRunId)) {
            (childrenByParent[tr.parentTaskRunId] ??= []).push(tr)
        } else {
            // root OR orphan-treated-as-root
            roots.push(tr)
        }
    }

    if (compareSiblings) {
        roots.sort(compareSiblings)
        for (const children of Object.values(childrenByParent)) {
            children.sort(compareSiblings)
        }
    }

    const ordered: Array<{task: T; depth: number}> = []
    const walk = (nodes: T[], depth: number): void => {
        for (const node of nodes) {
            // depth is computed during the walk — order-independent, parents resolve before children
            ordered.push({task: node, depth})
            const children = childrenByParent[node.id]
            if (children) {
                walk(children, depth + 1)
            }
        }
    }
    walk(roots, 0)

    return ordered
}
